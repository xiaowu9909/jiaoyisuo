package com.vaultpi.admin.controller;

import com.vaultpi.admin.dto.VirtualTrendRequest;
import com.vaultpi.admin.dto.TopRealCoinsRequest;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.config.RequireLoginInterceptor;
import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.exchange.entity.ExchangeOrder;
import com.vaultpi.exchange.repository.ExchangeOrderRepository;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.entity.VirtualTrendAudit;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.market.repository.VirtualTrendAuditRepository;
import com.vaultpi.market.service.VirtualMarketEngine;
import com.vaultpi.market.service.TopRealCoinsService;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/exchange", ApiPaths.V1 + "/admin/exchange" })
public class AdminExchangeController {

    private final ExchangeCoinRepository coinRepository;
    private final ExchangeOrderRepository orderRepository;
    private final CoinRepository baseCoinRepository;
    private final MemberRepository memberRepository;
    private final VirtualMarketEngine virtualMarketEngine;
    private final VirtualTrendAuditRepository virtualTrendAuditRepository;
    private final KrakenApiClient krakenApiClient;
    private final TopRealCoinsService topRealCoinsService;
    private final Counter trendSetCounter;
    private final MeterRegistry meterRegistry;

    /** 虚拟盘趋势设置冷却：每交易对每小时最多 1 次 */
    private static final long TREND_COOLDOWN_MS = 3600_000L;
    private final Map<String, Long> lastTrendTimeBySymbol = new ConcurrentHashMap<>();

    public AdminExchangeController(ExchangeCoinRepository coinRepository,
                                   ExchangeOrderRepository orderRepository,
                                   CoinRepository baseCoinRepository,
                                   MemberRepository memberRepository,
                                   VirtualMarketEngine virtualMarketEngine,
                                   VirtualTrendAuditRepository virtualTrendAuditRepository,
                                   KrakenApiClient krakenApiClient,
                                   TopRealCoinsService topRealCoinsService,
                                   MeterRegistry meterRegistry) {
        this.coinRepository = coinRepository;
        this.orderRepository = orderRepository;
        this.baseCoinRepository = baseCoinRepository;
        this.memberRepository = memberRepository;
        this.virtualMarketEngine = virtualMarketEngine;
        this.virtualTrendAuditRepository = virtualTrendAuditRepository;
        this.krakenApiClient = krakenApiClient;
        this.topRealCoinsService = topRealCoinsService;
        this.trendSetCounter = meterRegistry.counter("vaultpi.virtual.trend.sets.total", "type", "set");
        this.meterRegistry = meterRegistry;
    }

    private static java.math.BigDecimal toBigDecimal(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        if (v instanceof java.math.BigDecimal bd) return bd;
        try {
            return new java.math.BigDecimal(v.toString());
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private static String baseFromSymbol(String symbol) {
        if (symbol == null || !symbol.contains("/")) return null;
        String base = symbol.split("/")[0];
        return base != null ? base.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String normalizeUsdtSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        // 统一成 BASE/USDT（KrakenApiClient.fetchAllTickers 返回的就是这个格式，但防御一下）
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        if (!s.contains("/")) return null;
        String[] parts = s.split("/");
        if (parts.length < 2) return null;
        String base = parts[0];
        String quote = parts[1];
        if (!"USDT".equals(quote)) return null;
        return base + "/USDT";
    }

    private static boolean isValidBaseSymbol(String base) {
        if (base == null) return false;
        String b = base.trim().toUpperCase(Locale.ROOT);
        // 交易对币种通常是字母开头的代码（例如 BTC/ETH/XRP），不应出现纯数字/超短怪值
        // 例如 "2/USDT" 这种会导致前端不停请求无意义 symbol。
        if (b.length() < 2 || b.length() > 16) return false;
        return b.matches("^[A-Z][A-Z0-9]*$");
    }

    // --- Coin Management ---

    @GetMapping("/coin/list")
    public Result<List<ExchangeCoin>> coinList() {
        return Result.ok(coinRepository.findAll());
    }

    @PostMapping("/coin/add")
    public Result<ExchangeCoin> addCoin(@RequestBody ExchangeCoin coin) {
        if (coin.getSymbol() == null) return Result.fail(400, "交易对名称不能为空");
        if (coinRepository.findBySymbol(coin.getSymbol()).isPresent()) {
            return Result.fail(400, "交易对已存在");
        }
        ExchangeCoin saved = coinRepository.save(coin);
        if (Boolean.TRUE.equals(saved.getVirtual())) virtualMarketEngine.register(saved);
        return Result.ok(saved);
    }

    @PostMapping("/coin/update")
    public Result<ExchangeCoin> updateCoin(@RequestBody ExchangeCoin coin) {
        if (coin.getId() == null) return Result.fail(400, "ID 不能为空");
        ExchangeCoin existing = coinRepository.findById(coin.getId()).orElse(null);
        if (existing == null) return Result.fail(404, "交易对不存在");

        existing.setEnable(coin.getEnable());
        if (coin.getSymbol() != null) existing.setSymbol(coin.getSymbol());
        if (coin.getBaseSymbol() != null) existing.setBaseSymbol(coin.getBaseSymbol());
        if (coin.getCoinSymbol() != null) existing.setCoinSymbol(coin.getCoinSymbol());
        if (coin.getBaseCoinPrecision() != null) existing.setBaseCoinPrecision(coin.getBaseCoinPrecision());
        if (coin.getCoinPrecision() != null) existing.setCoinPrecision(coin.getCoinPrecision());
        if (coin.getVirtual() != null) existing.setVirtual(coin.getVirtual());
        if (coin.getCustomPrice() != null) existing.setCustomPrice(coin.getCustomPrice());
        if (coin.getCustomPriceLow() != null) existing.setCustomPriceLow(coin.getCustomPriceLow());
        if (coin.getCustomPriceHigh() != null) existing.setCustomPriceHigh(coin.getCustomPriceHigh());
        if (coin.getVirtualActivity() != null) existing.setVirtualActivity(coin.getVirtualActivity());
        if (coin.getTrendDirection() != null) existing.setTrendDirection(coin.getTrendDirection());
        if (coin.getTrendPercent() != null) existing.setTrendPercent(coin.getTrendPercent());
        if (coin.getTrendDuration() != null) existing.setTrendDuration(coin.getTrendDuration());
        if (coin.getTrendStartTime() != null) existing.setTrendStartTime(coin.getTrendStartTime());
        if (coin.getTrendStartPrice() != null) existing.setTrendStartPrice(coin.getTrendStartPrice());
        existing.setVirtualDriftDaily(coin.getVirtualDriftDaily());
        existing.setVirtualVolatility(coin.getVirtualVolatility());
        existing.setVirtualTickSize(coin.getVirtualTickSize());

        existing = coinRepository.save(existing);
        if (Boolean.TRUE.equals(existing.getVirtual())) virtualMarketEngine.register(existing);
        return Result.ok(existing);
    }

    /** 设置虚拟盘行情趋势：在 duration 秒内上涨/下跌 percent%，单次涨跌幅≤10%，周期≥5 分钟，每交易对每小时限 1 次；并写入审计日志 */
    @PostMapping("/coin/trend")
    public Result<ExchangeCoin> setVirtualTrend(@Valid @RequestBody VirtualTrendRequest req,
                                                HttpServletRequest request,
                                                HttpSession session) {
        Long adminId = (Long) session.getAttribute(RequireLoginInterceptor.SESSION_MEMBER_ID);
        if (adminId == null) return Result.fail(401, "请先登录");
        String symbol = req.getSymbol().trim();
        String direction = req.getDirection();
        if (!("UP".equalsIgnoreCase(direction) || "DOWN".equalsIgnoreCase(direction)))
            return Result.fail(400, "方向须为 UP 或 DOWN");
        long now = System.currentTimeMillis();
        Long last = lastTrendTimeBySymbol.get(symbol);
        if (last != null && (now - last) < TREND_COOLDOWN_MS)
            return Result.fail(429, "该交易对趋势设置冷却中，请 1 小时后再试");
        ExchangeCoin coin = coinRepository.findBySymbol(symbol).orElse(null);
        if (coin == null) return Result.fail(404, "交易对不存在");
        if (!Boolean.TRUE.equals(coin.getVirtual())) return Result.fail(400, "仅支持虚拟盘设置趋势");
        BigDecimal base = virtualMarketEngine.getCurrentPrice(symbol);
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) base = virtualCenterPrice(coin);
        if (base == null) return Result.fail(400, "请先配置虚拟盘价格或价格区间");
        coin.setTrendDirection(direction.toUpperCase());
        coin.setTrendPercent(req.getPercent());
        coin.setTrendDuration(req.getDuration());
        coin.setTrendStartTime(now);
        coin.setTrendStartPrice(base);
        ExchangeCoin saved = coinRepository.save(coin);
        virtualMarketEngine.updateCoin(saved);
        lastTrendTimeBySymbol.put(symbol, now);

        String ip = request.getRemoteAddr();
        if (request.getHeader("X-Forwarded-For") != null && !request.getHeader("X-Forwarded-For").isBlank())
            ip = request.getHeader("X-Forwarded-For").split(",")[0].trim();
        VirtualTrendAudit audit = new VirtualTrendAudit();
        audit.setAdminId(adminId);
        audit.setSymbol(symbol);
        audit.setDirection(direction.toUpperCase());
        audit.setPercent(req.getPercent());
        audit.setDuration(req.getDuration());
        audit.setOperationTime(now);
        audit.setIp(ip != null && ip.length() > 64 ? ip.substring(0, 64) : ip);
        audit.setStartPrice(base);
        virtualTrendAuditRepository.save(audit);
        trendSetCounter.increment();
        meterRegistry.counter("vaultpi.virtual.trend.sets.by.symbol", "symbol", symbol).increment();

        return Result.ok(saved);
    }

    /** 虚拟盘趋势设置审计日志（分页），支持按 symbol、adminId 筛选 */
    @GetMapping("/coin/trend/audit")
    public Result<Map<String, Object>> trendAuditPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Long adminId) {
        var sort = Sort.by(Sort.Direction.DESC, "operationTime");
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.min(50, Math.max(1, pageSize)), sort);
        Page<VirtualTrendAudit> page;
        boolean hasSymbol = symbol != null && !symbol.isBlank();
        if (hasSymbol && adminId != null) {
            page = virtualTrendAuditRepository.findBySymbolAndAdminIdOrderByOperationTimeDesc(symbol.trim(), adminId, pageable);
        } else if (hasSymbol) {
            page = virtualTrendAuditRepository.findBySymbolOrderByOperationTimeDesc(symbol.trim(), pageable);
        } else if (adminId != null) {
            page = virtualTrendAuditRepository.findByAdminIdOrderByOperationTimeDesc(adminId, pageable);
        } else {
            page = virtualTrendAuditRepository.findAllByOrderByOperationTimeDesc(pageable);
        }
        List<Map<String, Object>> content = page.getContent().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("adminId", a.getAdminId());
            m.put("symbol", a.getSymbol());
            m.put("direction", a.getDirection());
            m.put("percent", a.getPercent());
            m.put("duration", a.getDuration());
            m.put("operationTime", a.getOperationTime());
            m.put("ip", a.getIp());
            m.put("startPrice", a.getStartPrice());
            Member admin = memberRepository.findById(a.getAdminId()).orElse(null);
            m.put("adminUsername", admin != null ? admin.getUsername() : null);
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    /** 清除虚拟盘行情趋势 */
    @PostMapping("/coin/trend/clear")
    public Result<ExchangeCoin> clearVirtualTrend(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null) return Result.fail(400, "ID 不能为空");
        ExchangeCoin coin = coinRepository.findById(id).orElse(null);
        if (coin == null) return Result.fail(404, "交易对不存在");
        coin.setTrendDirection(null);
        coin.setTrendPercent(null);
        coin.setTrendDuration(null);
        coin.setTrendStartTime(null);
        coin.setTrendStartPrice(null);
        ExchangeCoin saved = coinRepository.save(coin);
        virtualMarketEngine.updateCoin(saved);
        return Result.ok(saved);
    }

    private BigDecimal virtualCenterPrice(ExchangeCoin c) {
        if (c.getCustomPriceLow() != null && c.getCustomPriceHigh() != null
                && c.getCustomPriceLow().compareTo(c.getCustomPriceHigh()) <= 0)
            return c.getCustomPriceLow().add(c.getCustomPriceHigh()).divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP);
        if (c.getCustomPrice() != null) return c.getCustomPrice();
        return null;
    }

    @PostMapping("/coin/delete")
    public Result<String> deleteCoin(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null) return Result.fail(400, "ID 不能为空");
        coinRepository.deleteById(id);
        return Result.ok("删除成功");
    }

    /**
     * 从 Kraken 获取热门交易对并导入到 exchange_coin（仅替换非虚拟盘）。
     * 热门排序口径：turnover（成交额）= volume * close
     */
    @PostMapping("/coin/rebuild-top-real")
    @Transactional
    public Result<Map<String, Object>> rebuildTopRealCoins(@RequestBody(required = false) TopRealCoinsRequest req) {
        // 交由共享服务处理：本地 H2 / 生产 MySQL 走同一套逻辑，并且通过快照表避免重启后列表波动
        return topRealCoinsService.applyOrRebuildTopRealCoins(req, false);
    }

    // --- Order Auditing ---

    @GetMapping("/order/page")
    public Result<Map<String, Object>> orderPage(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) Long memberId,
                                                @RequestParam(required = false) String symbol) {
        var sort = Sort.by(Sort.Direction.DESC, "createTime");
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, pageSize), sort);
        
        Page<ExchangeOrder> page;
        if (memberId != null && symbol != null && !symbol.isEmpty()) {
            page = orderRepository.findByMemberIdAndSymbol(memberId, symbol, pageable);
        } else if (memberId != null) {
            page = orderRepository.findByMemberId(memberId, pageable);
        } else if (symbol != null && !symbol.isEmpty()) {
            page = orderRepository.findBySymbol(symbol, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }

        List<Map<String, Object>> content = page.getContent().stream().map(o -> {
            Member m = memberRepository.findById(o.getMemberId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", o.getOrderId());
            map.put("memberId", o.getMemberId());
            map.put("username", m != null ? m.getUsername() : "?");
            map.put("symbol", o.getSymbol());
            map.put("direction", o.getDirection());
            map.put("type", o.getType());
            map.put("price", o.getPrice());
            map.put("amount", o.getAmount());
            map.put("tradedAmount", o.getTradedAmount());
            map.put("status", o.getStatus());
            map.put("createTime", o.getCreateTime() != null ? o.getCreateTime().toString() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }
}
