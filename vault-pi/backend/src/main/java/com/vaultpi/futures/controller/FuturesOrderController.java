package com.vaultpi.futures.controller;

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
import com.vaultpi.futures.dto.FuturesOrderAddRequest;
import com.vaultpi.futures.entity.ContractOrder;
import com.vaultpi.futures.entity.ContractPosition;
import com.vaultpi.futures.repository.ContractOrderRepository;
import com.vaultpi.futures.repository.ContractPositionRepository;
import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.user.service.MemberService;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/futures", ApiPaths.V1 + "/futures" })
public class FuturesOrderController {

    private final ContractOrderRepository orderRepository;
    private final ContractPositionRepository positionRepository;
    private final CoinRepository coinRepository;
    private final MemberWalletRepository memberWalletRepository;
    private final WalletService walletService;
    private final MemberService memberService;
    private final KrakenApiClient krakenApiClient;

    public FuturesOrderController(ContractOrderRepository orderRepository,
                                  ContractPositionRepository positionRepository,
                                  CoinRepository coinRepository,
                                  MemberWalletRepository memberWalletRepository,
                                  WalletService walletService,
                                  MemberService memberService,
                                  KrakenApiClient krakenApiClient) {
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.coinRepository = coinRepository;
        this.memberWalletRepository = memberWalletRepository;
        this.walletService = walletService;
        this.memberService = memberService;
        this.krakenApiClient = krakenApiClient;
    }

    @PostMapping("/order/add")
    @Transactional
    public Result<ContractOrder> add(@Valid @RequestBody FuturesOrderAddRequest req, HttpSession session) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        String bizErr = req.validateBusiness();
        if (bizErr != null) {
            return Result.fail(ErrorCode.PARAM_INVALID, bizErr);
        }

        String symbol = req.getSymbol();
        String direction = req.getDirection();
        String type = req.getType();
        BigDecimal price = req.getPrice();
        BigDecimal amount = req.getAmount();

        int maxLeverage = memberService.getMaxLeverage(memberId);
        int leverage = req.getLeverage() != null ? req.getLeverage() : maxLeverage;
        if (leverage > maxLeverage) leverage = maxLeverage;
        if (leverage < 1) leverage = 1;

        BigDecimal notional = price.multiply(amount);
        BigDecimal requiredMargin = notional.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        String baseSymbol = symbol.contains("/") ? symbol.split("/")[1] : symbol;
        Optional<Coin> coinOpt = coinRepository.findByUnit(baseSymbol);
        if (coinOpt.isEmpty()) {
            return Result.fail(ErrorCode.PARAM_INVALID, "交易币种不存在: " + baseSymbol);
        }
        Long coinId = coinOpt.get().getId();

        MemberWallet wallet = walletService.getOrCreateWallet(memberId, coinId);
        BigDecimal available = wallet.getBalance().subtract(wallet.getFrozenBalance());
        if (available.compareTo(requiredMargin) < 0) {
            return Result.fail(ErrorCode.BALANCE_INSUFFICIENT);
        }

        wallet.setBalance(wallet.getBalance().subtract(requiredMargin));
        wallet.setFrozenBalance(wallet.getFrozenBalance().add(requiredMargin));
        memberWalletRepository.save(wallet);

        String orderId = "F" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        ContractOrder order = new ContractOrder();
        order.setOrderId(orderId);
        order.setMemberId(memberId);
        order.setSymbol(symbol);
        order.setDirection(direction);
        order.setType(type);
        order.setLeverage(leverage);
        order.setPrice(price != null ? price : BigDecimal.ZERO);
        order.setAmount(amount);
        order.setMargin(requiredMargin);
        order.setStatus("PENDING");
        order.setCreateTime(Instant.now());

        return Result.ok(orderRepository.save(order));
    }

    @PostMapping("/order/cancel")
    @Transactional
    public Result<String> cancel(@RequestBody Map<String, String> body, HttpSession session) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) return Result.fail(401, "请先登录");

        String orderId = body.get("orderId");
        if (orderId == null) return Result.fail(400, "缺少 orderId");

        ContractOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return Result.fail(404, "订单不存在");
        if (!order.getMemberId().equals(memberId)) return Result.fail(403, "无权操作");
        if (!"PENDING".equals(order.getStatus())) return Result.fail(400, "订单状态不可撤销");

        // Unfreeze Margin
        String baseSymbol = order.getSymbol().split("/")[1];
        coinRepository.findByUnit(baseSymbol).ifPresent(coin -> {
            memberWalletRepository.findByMemberIdAndCoinId(memberId, coin.getId()).ifPresent(w -> {
                w.setFrozenBalance(w.getFrozenBalance().subtract(order.getMargin()));
                w.setBalance(w.getBalance().add(order.getMargin()));
                memberWalletRepository.save(w);
            });
        });

        order.setStatus("CANCELED");
        orderRepository.save(order);
        return Result.ok("撤单成功");
    }


    @PostMapping("/position/close")
    @Transactional
    public Result<String> closePosition(@RequestBody Map<String, String> body, HttpSession session) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) return Result.fail(401, "请先登录");

        String posIdStr = body.get("positionId");
        if (posIdStr == null) return Result.fail(400, "缺少 positionId");
        Long positionId = Long.parseLong(posIdStr);

        ContractPosition pos = positionRepository.findById(positionId).orElse(null);
        if (pos == null) return Result.fail(404, "持仓不存在");
        if (!pos.getMemberId().equals(memberId)) return Result.fail(403, "无权操作");
        if (!"OPEN".equals(pos.getStatus())) return Result.fail(400, "该持仓并非开启状态");

        // Fetch current price for settlement (Kraken)
        BigDecimal currentPrice = krakenApiClient.fetchCurrentPrice(pos.getSymbol());
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            return Result.fail(500, "无法获取 Kraken 实时价格用以平仓");
        }

        // Calculate PNL
        BigDecimal pnl;
        if ("LONG".equals(pos.getDirection())) {
            pnl = currentPrice.subtract(pos.getAvgPrice()).multiply(pos.getVolume());
        } else {
            pnl = pos.getAvgPrice().subtract(currentPrice).multiply(pos.getVolume());
        }

        BigDecimal rawRefund = pos.getMargin().add(pnl);
        final BigDecimal refund = rawRefund.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : rawRefund;

        // Pay to wallet
        String baseSymbol = pos.getSymbol().split("/")[1];
        coinRepository.findByUnit(baseSymbol).ifPresent(coin -> {
            memberWalletRepository.findByMemberIdAndCoinId(memberId, coin.getId()).ifPresent(w -> {
                w.setBalance(w.getBalance().add(refund));
                memberWalletRepository.save(w);
            });
        });

        pos.setStatus("CLOSED");
        pos.setRealizedPnl(pnl);
        positionRepository.save(pos);
        return Result.ok("平仓成功! 释放余额: " + refund.setScale(4, RoundingMode.HALF_UP));
    }

    // List Endpoints
    @GetMapping("/order/current")
    public Result<List<ContractOrder>> currentOrders(HttpSession session) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) return Result.fail(401, "请先登录");
        return Result.ok(orderRepository.findByStatus("PENDING").stream()
                .filter(o -> o.getMemberId().equals(memberId))
                .collect(Collectors.toList()));
    }

    @GetMapping("/position/current")
    public Result<List<ContractPosition>> currentPositions(HttpSession session) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) return Result.fail(401, "请先登录");
        return Result.ok(positionRepository.findByMemberIdAndStatus(memberId, "OPEN"));
    }

    @GetMapping("/order/history")
    public Result<Map<String, Object>> historyOrders(HttpSession session,
                                                    @RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) return Result.fail(401, "请先登录");
        var page = orderRepository.findByMemberIdOrderByCreateTimeDesc(memberId, PageRequest.of(Math.max(0, pageNo - 1), pageSize));
        return Result.ok(Map.of(
            "content", page.getContent(),
            "totalElements", page.getTotalElements(),
            "totalPages", page.getTotalPages()
        ));
    }
}
