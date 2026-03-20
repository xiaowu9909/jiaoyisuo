package com.vaultpi.exchange.controller;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.service.WalletService;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.ErrorCode;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import com.vaultpi.common.SessionUtil;
import com.vaultpi.config.LockProvider;
import com.vaultpi.exchange.dto.OrderAddRequest;
import com.vaultpi.exchange.dto.OrderCancelRequest;
import com.vaultpi.exchange.entity.ExchangeOrder;
import com.vaultpi.exchange.repository.ExchangeOrderRepository;
import com.vaultpi.exchange.service.IdempotencyService;
import com.vaultpi.exchange.service.MatchService;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import io.micrometer.core.instrument.MeterRegistry;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 委托订单：下单（冻结资产）、撤单（解冻）、当前委托、历史委托
 */
@Tag(name = "现货订单", description = "下单、撤单、当前委托、历史委托")
@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE, ApiPaths.V1 })
public class OrderController {

    private final ExchangeOrderRepository orderRepository;
    private final ExchangeCoinRepository exchangeCoinRepository;
    private final CoinRepository coinRepository;
    private final MemberWalletRepository memberWalletRepository;
    private final WalletService walletService;
    private final MatchService matchService;
    private final MemberRepository memberRepository;
    private final LockProvider lockProvider;
    private final IdempotencyService idempotencyService; // 无 Redis 时为 null
    private final MeterRegistry meterRegistry;

    public OrderController(ExchangeOrderRepository orderRepository,
                          ExchangeCoinRepository exchangeCoinRepository,
                          CoinRepository coinRepository,
                          MemberWalletRepository memberWalletRepository,
                          WalletService walletService,
                          MatchService matchService,
                          MemberRepository memberRepository,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) LockProvider lockProvider,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) IdempotencyService idempotencyService,
                          MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.coinRepository = coinRepository;
        this.memberWalletRepository = memberWalletRepository;
        this.walletService = walletService;
        this.matchService = matchService;
        this.memberRepository = memberRepository;
        this.lockProvider = lockProvider;
        this.idempotencyService = idempotencyService;
        this.meterRegistry = meterRegistry;
    }

    /** 风控：禁用账号不能下单/撤单 */
    private void requireNormalMember(Long memberId) {
        Member m = memberRepository.findById(memberId).orElse(null);
        if (m == null || !"NORMAL".equals(m.getStatus())) {
            throw new IllegalStateException("该帐号已被禁用，无法进行交易");
        }
    }

    @Operation(summary = "下单", description = "现货限价/市价单，需登录；支持 X-Idempotency-Key 防重复提交；受限流与分布式锁保护")
    @PostMapping("/order/add")
    @Transactional
    public Result<ExchangeOrder> add(@Valid @RequestBody OrderAddRequest req, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        try {
            requireNormalMember(memberId);
        } catch (IllegalStateException e) {
            return Result.fail(ErrorCode.FORBIDDEN, e.getMessage());
        }
        String bizErr = req.validateBusiness();
        if (bizErr != null) return Result.fail(ErrorCode.PARAM_INVALID, bizErr);
        Optional<ExchangeCoin> coinOpt = exchangeCoinRepository.findBySymbol(req.getSymbol());
        if (coinOpt.isPresent()) {
            ExchangeCoin ex = coinOpt.get();
            // 下架交易对：仅影响新增订单，不影响历史订单的撤单解冻
            if (!Boolean.TRUE.equals(ex.getEnable())) {
                return Result.fail(ErrorCode.SYMBOL_NOT_FOUND, "交易对已下架");
            }
            String scaleErr = req.validatePriceAndAmountScale(ex);
            if (scaleErr != null) return Result.fail(ErrorCode.PARAM_INVALID, scaleErr);
            BigDecimal amount = req.getAmount();
            if (amount != null) {
                if (ex.getMinAmount() != null && amount.compareTo(ex.getMinAmount()) < 0)
                    return Result.fail(ErrorCode.PARAM_INVALID, "数量不能小于 " + ex.getMinAmount());
                if (ex.getMaxAmount() != null && amount.compareTo(ex.getMaxAmount()) > 0)
                    return Result.fail(ErrorCode.PARAM_INVALID, "数量不能大于 " + ex.getMaxAmount());
            }
            if ("LIMIT".equals(req.getType()) && req.getPrice() != null && amount != null && ex.getMinNotional() != null) {
                BigDecimal notional = req.getPrice().multiply(amount);
                if (notional.compareTo(ex.getMinNotional()) < 0)
                    return Result.fail(ErrorCode.PARAM_INVALID, "名义价值不能小于 " + ex.getMinNotional());
            }
        }

        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = req.getIdempotencyKey();
        }
        String existing = idempotencyService != null ? idempotencyService.checkAndMarkProcessing(idempotencyKey) : null;
        if (existing != null) {
            if ("processing".equals(existing)) {
                return Result.fail(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return orderRepository.findById(existing)
                .map(o -> Result.ok(o, "该请求已处理，订单号 " + existing))
                .orElse(Result.fail(ErrorCode.ORDER_NOT_FOUND));
        }

        String symbol = req.getSymbol();
        String direction = req.getDirection();
        String type = req.getType();
        BigDecimal price = req.getPrice();
        BigDecimal amount = req.getAmount();

        ExchangeOrder order;
        try {
            com.vaultpi.config.LockProvider lp = this.lockProvider != null
                    ? this.lockProvider
                    : new com.vaultpi.config.LockProvider() {
                        @Override
                        public <T> T runWithLock(String lockKey, java.util.function.Supplier<T> supplier) {
                            return supplier.get();
                        }
                    };
            order = lp.runWithLock("vaultpi:lock:order:member:" + memberId + ":symbol:" + symbol, () -> {
                Optional<ExchangeCoin> exOpt = exchangeCoinRepository.findBySymbol(symbol);
                if (exOpt.isEmpty()) throw new IllegalArgumentException("交易对不存在");
                ExchangeCoin ex = exOpt.get();

                if ("LIMIT".equals(type)) {
                    BigDecimal toFreeze;
                    Long coinId;
                    if ("BUY".equals(direction)) {
                        toFreeze = price.multiply(amount);
                        coinId = coinRepository.findByUnit(ex.getBaseSymbol()).map(Coin::getId).orElse(null);
                    } else {
                        toFreeze = amount;
                        coinId = coinRepository.findByUnit(ex.getCoinSymbol()).map(Coin::getId).orElse(null);
                    }
                    if (coinId == null) throw new IllegalArgumentException("交易对币种配置异常");
                    MemberWallet wallet = walletService.getOrCreateWallet(memberId, coinId);
                    BigDecimal available = wallet.getBalance().subtract(wallet.getFrozenBalance());
                    if (available.compareTo(toFreeze) < 0) throw new IllegalStateException("余额不足");
                    wallet.setBalance(wallet.getBalance().subtract(toFreeze));
                    wallet.setFrozenBalance(wallet.getFrozenBalance().add(toFreeze));
                    memberWalletRepository.save(wallet);
                }

                String orderId = "E" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
                ExchangeOrder o = new ExchangeOrder();
                o.setOrderId(orderId);
                o.setMemberId(memberId);
                o.setSymbol(symbol);
                o.setDirection(direction);
                o.setType(type);
                o.setPrice(price != null && price.compareTo(BigDecimal.ZERO) > 0 ? price : BigDecimal.ZERO);
                o.setAmount(amount);
                o.setTradedAmount(BigDecimal.ZERO);
                o.setStatus("TRADING");
                o.setCreateTime(Instant.now());
                o = orderRepository.save(o);
                if ("LIMIT".equals(type)) {
                    matchService.tryMatch(o.getOrderId());
                    o = orderRepository.findById(o.getOrderId()).orElse(o);
                }
                return o;
            });
        } catch (IllegalArgumentException e) {
            meterRegistry.counter("vaultpi.order.failures", "reason", "validation").increment();
            if ("交易对不存在".equals(e.getMessage())) return Result.fail(ErrorCode.SYMBOL_NOT_FOUND);
            return Result.fail(ErrorCode.PARAM_INVALID, e.getMessage());
        } catch (IllegalStateException e) {
            meterRegistry.counter("vaultpi.order.failures", "reason", "state").increment();
            if ("余额不足".equals(e.getMessage())) return Result.fail(ErrorCode.BALANCE_INSUFFICIENT);
            return Result.fail(ErrorCode.FORBIDDEN, e.getMessage());
        }

        if (idempotencyService != null) idempotencyService.setResult(idempotencyKey, order.getOrderId());
        return Result.ok(order);
    }

    @Operation(summary = "撤单", description = "撤销未完全成交的订单")
    @PostMapping("/order/cancel")
    @Transactional
    public Result<String> cancel(@Valid @RequestBody OrderCancelRequest req, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        try {
            requireNormalMember(memberId);
        } catch (IllegalStateException e) {
            return Result.fail(ErrorCode.FORBIDDEN, e.getMessage());
        }
        String orderId = req.getOrderId();
        ExchangeOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return Result.fail(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getMemberId().equals(memberId)) {
            return Result.fail(ErrorCode.FORBIDDEN);
        }
        if (!"TRADING".equals(order.getStatus())) {
            return Result.fail(ErrorCode.ORDER_STATE_INVALID);
        }
        // 解冻：仅退回未成交部分的冻结
        if ("LIMIT".equals(order.getType())) {
            BigDecimal remain = order.getAmount().subtract(order.getTradedAmount());
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                Optional<ExchangeCoin> exOpt = exchangeCoinRepository.findBySymbol(order.getSymbol());
                if (exOpt.isPresent()) {
                    ExchangeCoin ex = exOpt.get();
                    BigDecimal toUnfreeze;
                    Long coinId;
                    if ("BUY".equals(order.getDirection())) {
                        toUnfreeze = order.getPrice().multiply(remain);
                        coinId = coinRepository.findByUnit(ex.getBaseSymbol()).map(Coin::getId).orElse(null);
                    } else {
                        toUnfreeze = remain;
                        coinId = coinRepository.findByUnit(ex.getCoinSymbol()).map(Coin::getId).orElse(null);
                    }
                    if (coinId != null && toUnfreeze.compareTo(BigDecimal.ZERO) > 0) {
                        memberWalletRepository.findByMemberIdAndCoinId(memberId, coinId).ifPresent(w -> {
                            w.setFrozenBalance(w.getFrozenBalance().subtract(toUnfreeze));
                            w.setBalance(w.getBalance().add(toUnfreeze));
                            memberWalletRepository.save(w);
                        });
                    }
                }
            }
        }
        order.setStatus("CANCELED");
        orderRepository.save(order);
        matchService.removeOrderFromBook(orderId);
        return Result.ok("已撤单");
    }

    @Operation(summary = "当前委托", description = "当前账号未成交订单列表")
    @GetMapping("/uc/order/current")
    public Result<List<Map<String, Object>>> current(HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        List<ExchangeOrder> list = orderRepository.findByMemberIdAndStatusOrderByCreateTimeDesc(memberId, "TRADING");
        List<Map<String, Object>> out = list.stream().map(this::orderToMap).collect(Collectors.toList());
        return Result.ok(out);
    }

    @Operation(summary = "历史委托", description = "当前账号历史委托分页")
    @GetMapping("/uc/order/history")
    public Result<Map<String, Object>> history(HttpServletRequest request,
                                                @RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = SessionUtil.getMemberId(request);
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(50, pageSize)),
            Sort.by(Sort.Direction.DESC, "createTime"));
        var page = orderRepository.findByMemberIdOrderByCreateTimeDesc(memberId, pageable);
        List<Map<String, Object>> content = page.getContent().stream().map(this::orderToMap).collect(Collectors.toList());
        return Result.ok(Map.of(
            "content", content,
            "totalElements", page.getTotalElements(),
            "totalPages", page.getTotalPages()
        ));
    }

    /** 当前账号历史交易（全部委托记录，按创建时间倒序；含未成交/已撤单，便于与历史委托一致并看到所有记录） */
    @GetMapping("/uc/order/trade-history")
    public Result<Map<String, Object>> tradeHistory(HttpServletRequest request,
                                                    @RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = SessionUtil.getMemberId(request);
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(50, pageSize)),
            Sort.by(Sort.Direction.DESC, "createTime"));
        var page = orderRepository.findByMemberIdOrderByCreateTimeDesc(memberId, pageable);
        List<Map<String, Object>> content = page.getContent().stream().map(this::orderToTradeMap).collect(Collectors.toList());
        return Result.ok(Map.of(
            "content", content,
            "totalElements", page.getTotalElements(),
            "totalPages", page.getTotalPages()
        ));
    }

    /** 订单转通用 Map（当前委托、历史委托共用） */
    private Map<String, Object> orderToMap(ExchangeOrder o) {
        return Map.of(
            "orderId", o.getOrderId(),
            "symbol", o.getSymbol(),
            "direction", o.getDirection(),
            "type", o.getType(),
            "price", o.getPrice(),
            "amount", o.getAmount(),
            "tradedAmount", o.getTradedAmount(),
            "status", o.getStatus(),
            "createTime", o.getCreateTime() != null ? o.getCreateTime().toString() : ""
        );
    }

    /** 订单转交易流水 Map：在 orderToMap 基础上增加 totalAmount（价×成交量） */
    private Map<String, Object> orderToTradeMap(ExchangeOrder o) {
        Map<String, Object> m = new HashMap<>(orderToMap(o));
        BigDecimal price = o.getPrice() != null ? o.getPrice() : BigDecimal.ZERO;
        BigDecimal traded = o.getTradedAmount() != null ? o.getTradedAmount() : BigDecimal.ZERO;
        m.put("totalAmount", price.multiply(traded));
        return m;
    }
}
