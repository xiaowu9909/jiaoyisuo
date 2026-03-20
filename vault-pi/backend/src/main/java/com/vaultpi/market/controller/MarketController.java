package com.vaultpi.market.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.exchange.entity.ExchangeOrder;
import com.vaultpi.exchange.entity.ExchangeTrade;
import com.vaultpi.exchange.repository.ExchangeOrderRepository;
import com.vaultpi.exchange.repository.ExchangeTradeRepository;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.market.service.KlineCacheService;
import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.market.service.KrakenMarketRedisService;
import com.vaultpi.market.service.KrakenWebSocketRunner;
import com.vaultpi.market.service.VirtualMarketEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Random;

import static java.lang.Math.*;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/market", ApiPaths.V1 + "/market" })
public class MarketController {

    private static final Logger log = LoggerFactory.getLogger(MarketController.class);

    private final ExchangeCoinRepository exchangeCoinRepository;
    private final ExchangeOrderRepository orderRepository;
    private final ExchangeTradeRepository tradeRepository;
    private final VirtualMarketEngine virtualMarketEngine;
    private final KlineCacheService klineCacheService;
    private final KrakenApiClient krakenApiClient;
    private final KrakenMarketRedisService krakenMarketRedisService;
    private final KrakenWebSocketRunner krakenWebSocketRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${vaultpi.market.symbol-thumb-one-cache-ttl-ms:5000}")
    private long symbolThumbOneCacheTtlMs;

    @Value("${vaultpi.market.symbol-thumb-one.rest-min-interval-ms:5000}")
    private long symbolThumbOneRestMinIntervalMs;

    private static class ThumbOneCacheEntry {
        final Map<String, Object> thumb;
        final long atMs;

        ThumbOneCacheEntry(Map<String, Object> thumb, long atMs) {
            this.thumb = thumb;
            this.atMs = atMs;
        }
    }

    private final ConcurrentHashMap<String, ThumbOneCacheEntry> symbolThumbOneCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> symbolThumbOneLastRestAt = new ConcurrentHashMap<>();

    public MarketController(ExchangeCoinRepository exchangeCoinRepository,
                            ExchangeOrderRepository orderRepository,
                            ExchangeTradeRepository tradeRepository,
                            VirtualMarketEngine virtualMarketEngine,
                            @Autowired(required = false) KlineCacheService klineCacheService,
                            KrakenApiClient krakenApiClient,
                            @Autowired(required = false) KrakenMarketRedisService krakenMarketRedisService,
                            @Autowired(required = false) KrakenWebSocketRunner krakenWebSocketRunner) {
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.virtualMarketEngine = virtualMarketEngine;
        this.klineCacheService = klineCacheService;
        this.krakenApiClient = krakenApiClient;
        this.krakenMarketRedisService = krakenMarketRedisService;
        this.krakenWebSocketRunner = krakenWebSocketRunner;
    }

    @GetMapping("/symbol")
    public Result<List<ExchangeCoin>> symbol() {
        return Result.ok(exchangeCoinRepository.findByEnableTrue());
    }

    @GetMapping("/symbol-thumb")
    public Result<List<Map<String, Object>>> symbolThumb() {
        List<ExchangeCoin> coins = exchangeCoinRepository.findByEnableTrue();
        Map<String, Map<String, Object>> krakenDataMap = new HashMap<>();
        if (krakenMarketRedisService != null) {
            try {
                krakenDataMap.putAll(krakenMarketRedisService.getAllThumbs());
            } catch (Exception e) {
                log.warn("Redis thumb read failed, using REST: {}", e.getMessage());
            }
        }
        boolean needRest = coins.stream()
            .filter(c -> !Boolean.TRUE.equals(c.getVirtual()))
            .anyMatch(c -> !krakenDataMap.containsKey(c.getSymbol()));
        if (needRest) {
            if (krakenWebSocketRunner != null) {
                log.error("Kraken WS unavailable or Redis missing data, falling back to REST for symbolThumb (wsConnected={})",
                    krakenWebSocketRunner.isConnected());
            }
            krakenDataMap.putAll(krakenApiClient.fetchAllTickers());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (ExchangeCoin c : coins) {
            String symbol = c.getSymbol();
            Map<String, Object> m = new HashMap<>();
            m.put("symbol", symbol);
            m.put("baseSymbol", c.getBaseSymbol());
            m.put("coinSymbol", c.getCoinSymbol());

            if (Boolean.TRUE.equals(c.getVirtual())) {
                if (!virtualMarketEngine.isVirtual(symbol)) virtualMarketEngine.register(c);
                Map<String, Object> thumb = virtualMarketEngine.getThumb(symbol, c.getBaseSymbol(), c.getCoinSymbol());
                if (thumb != null) {
                    m.putAll(thumb);
                } else {
                    fallbackThumb(m);
                }
            } else {
                Map<String, Object> kData = krakenDataMap.get(symbol);
                if (kData != null) {
                    m.put("open", kData.get("open"));
                    m.put("close", kData.get("close"));
                    m.put("high", kData.get("high"));
                    m.put("low", kData.get("low"));
                    m.put("volume", kData.get("volume"));
                    m.put("turnover", kData.get("turnover"));
                    m.put("chg", kData.get("chg"));
                    m.put("change", kData.get("change"));
                } else {
                    fallbackThumb(m);
                }
            }
            list.add(m);
        }
        return Result.ok(list);
    }

    /**
     * 单个 symbol 的 thumb：用于详情页毫秒级刷新。
     * 真实行情优先读取 Redis（WS 写入），Redis 缺失时走 Kraken REST，
     * 同时使用短 TTL 内存缓存避免高频轮询打爆外部接口。
     */
    @GetMapping("/symbol-thumb-one")
    public Result<Map<String, Object>> symbolThumbOne(@RequestParam String symbol) {
        Optional<ExchangeCoin> opt = findCoinBySymbol(symbol);
        if (opt.isEmpty()) return Result.fail(404, "交易对不存在");
        ExchangeCoin coin = opt.get();

        String normalizedSymbol = coin.getSymbol();
        Map<String, Object> out = new HashMap<>();
        out.put("symbol", normalizedSymbol);
        out.put("baseSymbol", coin.getBaseSymbol());
        out.put("coinSymbol", coin.getCoinSymbol());

        long now = System.currentTimeMillis();

        // 虚拟盘：仍走引擎内存态（未来虚拟盘细分可再拆）
        if (Boolean.TRUE.equals(coin.getVirtual())) {
            if (!virtualMarketEngine.isVirtual(normalizedSymbol)) virtualMarketEngine.register(coin);
            Map<String, Object> thumb = virtualMarketEngine.getThumb(
                normalizedSymbol, coin.getBaseSymbol(), coin.getCoinSymbol()
            );
            if (thumb != null) {
                out.putAll(thumb);
                return Result.ok(out);
            }
            fallbackThumb(out);
            return Result.ok(out);
        }

        // 真实盘：优先 Redis
        if (krakenMarketRedisService != null) {
            try {
                Map<String, Object> redisThumb = krakenMarketRedisService.getThumb(normalizedSymbol);
                if (redisThumb != null && !redisThumb.isEmpty()) {
                    out.putAll(redisThumb);
                    return Result.ok(out);
                }
            } catch (Exception e) {
                log.warn("symbolThumbOne redis read failed symbol={}: {}", normalizedSymbol, e.getMessage());
            }
        }

        // Redis 不存在/过期：优先返回内存缓存（stale 也返回），并对 REST 触发做最小间隔限频
        ThumbOneCacheEntry cached = symbolThumbOneCache.get(normalizedSymbol);
        if (cached != null) {
            out.putAll(cached.thumb);
            boolean fresh = (now - cached.atMs) <= symbolThumbOneCacheTtlMs;
            if (!fresh) out.put("stale", true);
            // 直接返回（避免任何情况下 Kraken REST 被高频触发）
            // 只有在缓存不存在时才考虑 REST；或当缓存存在但过期且系统需要恢复时，再触发 REST（受最小间隔限制）
            if (fresh) return Result.ok(out);
        }

        long lastRestAt = symbolThumbOneLastRestAt.getOrDefault(normalizedSymbol, 0L);
        if (now - lastRestAt < symbolThumbOneRestMinIntervalMs) {
            // REST 仍在冷却期：若缓存有值就返回 stale，否则返回明确失败
            if (cached != null) return Result.ok(out);
            return Result.fail(503, "行情暂不可用（REST 冷却中）");
        }

        // 最后兜底：Kraken REST（严格限频）
        Map<String, Object> restThumb = krakenApiClient.fetchThumbForSymbol(normalizedSymbol);
        symbolThumbOneLastRestAt.put(normalizedSymbol, now);
        if (restThumb != null && !restThumb.isEmpty()) {
            out.putAll(restThumb);
            symbolThumbOneCache.put(normalizedSymbol, new ThumbOneCacheEntry(restThumb, now));
            return Result.ok(out);
        }

        // 不能返回伪造数据：详情页毫秒级拉取需要真实行情或明确失败
        return Result.fail(503, "行情暂不可用");
    }

    private void fallbackThumb(Map<String, Object> m) {
        BigDecimal close = "BTC/USDT".equals(m.get("symbol")) ? new BigDecimal("50000") : new BigDecimal("3000");
        m.put("open", close); m.put("close", close); m.put("high", close); m.put("low", close);
        m.put("chg", BigDecimal.ZERO); m.put("change", BigDecimal.ZERO);
        m.put("volume", BigDecimal.ZERO); m.put("turnover", BigDecimal.ZERO);
    }

    /** 按 symbol 查找交易对，支持 "BASE/QUOTE" 或 "BASE-QUOTE" 两种格式 */
    private Optional<ExchangeCoin> findCoinBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();

        // Normalize user input:
        // 1) trim whitespace
        // 2) some proxies/frameworks may keep %2F as literal in query params
        // 3) DB symbols are typically uppercase (e.g. ETH/USDT)
        String s = symbol.trim()
            .replace("%2F", "/").replace("%2f", "/")
            .replace("%2D", "-").replace("%2d", "-")
            .toUpperCase(Locale.ROOT);

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(s);
        candidates.add(s.replace("/", "-"));
        candidates.add(s.replace("-", "/"));

        for (String c : candidates) {
            Optional<ExchangeCoin> opt = exchangeCoinRepository.findBySymbol(c);
            if (opt.isPresent()) return opt;
        }
        return Optional.empty();
    }

    @GetMapping("/symbol-info")
    public Result<ExchangeCoin> symbolInfo(@RequestParam String symbol) {
        return findCoinBySymbol(symbol)
            .map(Result::ok)
            .orElse(Result.fail(404, "交易对不存在"));
    }

    /**
     * K 线：虚拟盘由引擎生成，实盘使用 Kraken OHLC。
     * 失败时回退基准价使用 Kraken Ticker 当前价。
     */
    @GetMapping("/kline")
    public Result<List<Map<String, Object>>> kline(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Long endTime) {
        Optional<ExchangeCoin> opt = findCoinBySymbol(symbol);
        if (opt.isEmpty()) return Result.fail(404, "交易对不存在");
        ExchangeCoin coin = opt.get();

        List<Map<String, Object>> cached = (klineCacheService != null) ? klineCacheService.get(symbol, interval, limit, endTime) : null;
        if (cached != null && !cached.isEmpty()) return Result.ok(cached);

        if (Boolean.TRUE.equals(coin.getVirtual())) {
            if (!virtualMarketEngine.isVirtual(symbol)) virtualMarketEngine.register(coin);
            List<Map<String, Object>> klineList = virtualMarketEngine.getKline(symbol, interval, limit, endTime);
            List<Map<String, Object>> out = klineList != null ? klineList : new ArrayList<>();
            if (klineCacheService != null) klineCacheService.put(symbol, interval, limit, endTime, out);
            return Result.ok(out);
        }

        String krakenPair = krakenApiClient.toKrakenPair(symbol);
        List<Map<String, Object>> list = new ArrayList<>();
        if (krakenPair != null) {
            int intervalMin = KrakenApiClient.intervalToMinutes(interval);
            Integer since = (endTime != null && endTime > 0) ? (int) (endTime / 1000) : null;
            list = krakenApiClient.fetchOhlc(krakenPair, intervalMin, since, limit);
            if (!list.isEmpty()) {
                if (klineCacheService != null) klineCacheService.put(symbol, interval, limit, endTime, list);
                return Result.ok(list);
            }
        }

        list = buildFallbackKlinesForRealMarket(symbol, krakenPair != null ? krakenPair : symbol, interval, limit);
        if (klineCacheService != null) klineCacheService.put(symbol, interval, limit, endTime, list);
        return Result.ok(list);
    }

    /**
     * 实盘 K 线回退（Kraken OHLC 失败时）：使用 GARCH(1,1) 生成收益序列。
     * 基准价取自 Kraken Ticker 当前价。
     */
    private List<Map<String, Object>> buildFallbackKlinesForRealMarket(String symbol, String krakenPairOrSymbol, String interval, int limit) {
        BigDecimal basePrice = krakenApiClient.fetchCurrentPrice(symbol);
        if (basePrice.compareTo(BigDecimal.ZERO) == 0) basePrice = defaultFallbackPrice(symbol);
        int intervalSeconds = "1m".equals(interval) ? 60 : "5m".equals(interval) ? 300 : "15m".equals(interval) ? 900 : 3600;
        long now = System.currentTimeMillis() / 1000;
        int n = Math.min(limit, 200);
        double volatility = 0.015;
        double lastVol = volatility;
        double lastReturn = 0.0;
        BigDecimal close = basePrice;
        List<Map<String, Object>> list = new ArrayList<>();
        var rng = ThreadLocalRandom.current();
        for (int i = n - 1; i >= 0; i--) {
            long time = now - (long) i * intervalSeconds;
            BigDecimal open = close;
            // GARCH(1,1): sigma^2 = 0.9*sigma^2 + 0.08*r^2 + 0.02*vol0^2
            lastVol = Math.sqrt(0.9 * lastVol * lastVol + 0.08 * lastReturn * lastReturn + 0.02 * volatility * volatility);
            double z = rng.nextGaussian();
            double ret = z * lastVol;
            lastReturn = ret;
            close = open.multiply(BigDecimal.valueOf(1 + ret)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal high = open.max(close).multiply(BigDecimal.valueOf(1.001)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal low = open.min(close).multiply(BigDecimal.valueOf(0.999)).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> klineMap = new HashMap<>();
            klineMap.put("time", time * 1000);
            klineMap.put("open", open);
            klineMap.put("high", high);
            klineMap.put("low", low);
            klineMap.put("close", close);
            klineMap.put("volume", BigDecimal.valueOf(rng.nextDouble(10, 500)).setScale(2, RoundingMode.HALF_UP));
            list.add(klineMap);
        }
        return list;
    }

    private static BigDecimal defaultFallbackPrice(String symbol) {
        return "BTC/USDT".equals(symbol) ? new BigDecimal("50000") : new BigDecimal("3000");
    }

    /** 盘口：虚拟盘由 GBM 引擎生成深度，实盘使用 Kraken Depth 并合并本地挂单 */
    @GetMapping("/plate")
    public Result<Map<String, Object>> plate(@RequestParam String symbol,
                                             @RequestParam(defaultValue = "20") int limit) {
        Optional<ExchangeCoin> coinOpt = findCoinBySymbol(symbol);
        if (coinOpt.isEmpty()) return Result.fail(404, "交易对不存在");
        ExchangeCoin coin = coinOpt.get();

        int max = Math.min(50, Math.max(10, limit));
        if (Boolean.TRUE.equals(coin.getVirtual())) {
            if (!virtualMarketEngine.isVirtual(symbol)) virtualMarketEngine.register(coin);
            Map<String, Object> plate = virtualMarketEngine.getPlate(symbol, max);
            if (plate != null) return Result.ok(plate);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("ask", new ArrayList<>());
            empty.put("bid", new ArrayList<>());
            return Result.ok(empty);
        }

        String krakenPair = krakenApiClient.toKrakenPair(symbol);
        Map<BigDecimal, BigDecimal> askMap = new LinkedHashMap<>();
        Map<BigDecimal, BigDecimal> bidMap = new LinkedHashMap<>();

        Map<String, Object> redisPlate = (krakenMarketRedisService != null) ? krakenMarketRedisService.getPlate(symbol) : null;
        if (redisPlate != null && (redisPlate.containsKey("ask") || redisPlate.containsKey("bid"))) {
            for (Object a : (List<?>) redisPlate.getOrDefault("ask", List.of())) {
                if (a instanceof Map<?, ?> row && row.size() >= 2) {
                    askMap.put(new BigDecimal(row.get("price").toString()), new BigDecimal(row.get("amount").toString()));
                }
            }
            for (Object b : (List<?>) redisPlate.getOrDefault("bid", List.of())) {
                if (b instanceof Map<?, ?> row && row.size() >= 2) {
                    bidMap.put(new BigDecimal(row.get("price").toString()), new BigDecimal(row.get("amount").toString()));
                }
            }
        }
        if (askMap.isEmpty() && bidMap.isEmpty() && krakenPair != null) {
            Map<String, List<List<Object>>> depth = krakenApiClient.fetchDepth(krakenPair, max);
            if (depth != null) {
                for (List<Object> a : depth.getOrDefault("asks", List.of())) {
                    if (a.size() >= 2) askMap.put(new BigDecimal(a.get(0).toString()), new BigDecimal(a.get(1).toString()));
                }
                for (List<Object> b : depth.getOrDefault("bids", List.of())) {
                    if (b.size() >= 2) bidMap.put(new BigDecimal(b.get(0).toString()), new BigDecimal(b.get(1).toString()));
                }
            }
        }

        List<ExchangeOrder> sells = orderRepository.findBySymbolAndDirectionAndStatusOrderByPriceAscCreateTimeAsc(
            symbol, "SELL", "TRADING", PageRequest.of(0, 200));
        List<ExchangeOrder> buys = orderRepository.findBySymbolAndDirectionAndStatusOrderByPriceDescCreateTimeAsc(
            symbol, "BUY", "TRADING", PageRequest.of(0, 200));

        for (ExchangeOrder o : sells) {
            BigDecimal remain = o.getAmount().subtract(o.getTradedAmount());
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                askMap.merge(o.getPrice(), remain, BigDecimal::add);
            }
        }
        for (ExchangeOrder o : buys) {
            BigDecimal remain = o.getAmount().subtract(o.getTradedAmount());
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                bidMap.merge(o.getPrice(), remain, BigDecimal::add);
            }
        }

        List<Map<String, Object>> askItems = askMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(max)
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("price", e.getKey());
                map.put("amount", e.getValue());
                return map;
            })
            .collect(Collectors.toList());

        List<Map<String, Object>> bidItems = bidMap.entrySet().stream()
            .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
            .limit(max)
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("price", e.getKey());
                map.put("amount", e.getValue());
                return map;
            })
            .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ask", askItems);
        out.put("bid", bidItems);
        return Result.ok(out);
    }

    /** 虚拟盘模拟成交：种子=交易对+当前分钟，保证任意界面显示统一成交量列表 */
    /** 最新成交: 虚拟盘=引擎生成+本地成交；实盘=币安历史+本地成交 */
    @GetMapping("/latest-trade")
    public Result<List<Map<String, Object>>> latestTrade(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "30") int size) {
        Optional<ExchangeCoin> coinOpt = findCoinBySymbol(symbol);
        if (coinOpt.isEmpty()) return Result.fail(404, "交易对不存在");
        ExchangeCoin coin = coinOpt.get();
        int limit = Math.min(100, Math.max(1, size));
        List<Map<String, Object>> data = new ArrayList<>();
        List<ExchangeTrade> list = tradeRepository.findBySymbolOrderByCreateTimeDesc(coin.getSymbol(), PageRequest.of(0, limit));
        list.forEach(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("price", t.getPrice());
            map.put("amount", t.getAmount());
            map.put("direction", t.getDirection());
            map.put("time", t.getCreateTime() != null ? t.getCreateTime().toEpochMilli() : 0);
            data.add(map);
        });
        if (Boolean.TRUE.equals(coin.getVirtual())) {
            if (!virtualMarketEngine.isVirtual(symbol)) virtualMarketEngine.register(coin);
            List<Map<String, Object>> engineTrades = virtualMarketEngine.getLatestTrades(symbol, limit);
            if (engineTrades != null && !engineTrades.isEmpty()) {
                data.addAll(engineTrades);
                data.sort((a, b) -> Long.compare((Long) b.get("time"), (Long) a.get("time")));
            }
            return Result.ok(data.stream().limit(limit).collect(Collectors.toList()));
        }

        String krakenPair = krakenApiClient.toKrakenPair(symbol);
        if (data.size() < limit && krakenPair != null) {
            List<Map<String, Object>> kTrades = krakenApiClient.fetchTrades(krakenPair, null, limit - data.size());
            for (Map<String, Object> t : kTrades) {
                Map<String, Object> map = new HashMap<>();
                map.put("price", t.get("price"));
                map.put("amount", t.get("amount"));
                map.put("direction", t.get("direction"));
                map.put("time", t.get("time"));
                data.add(map);
            }
        }

        // sort again by time DESC
        data.sort((a, b) -> Long.compare((Long) b.get("time"), (Long) a.get("time")));
        return Result.ok(data.stream().limit(limit).collect(Collectors.toList()));
    }

    /**
     * 兼容性端点: plate-mini
     * 前端可能期望简化的盘口数据
     */
    @GetMapping("/plate-mini")
    public Result<Map<String, Object>> plateMini(@RequestParam String symbol,
                                                 @RequestParam(defaultValue = "5") int limit) {
        // 直接调用 plate 方法，但限制更小的数量
        return plate(symbol, limit);
    }

    /**
     * 兼容性端点: trade
     * 前端可能期望成交记录
     */
    @GetMapping("/trade")
    public Result<List<Map<String, Object>>> trade(@RequestParam String symbol,
                                                   @RequestParam(defaultValue = "30") int size) {
        // 直接调用 latest-trade 方法
        return latestTrade(symbol, size);
    }
}
