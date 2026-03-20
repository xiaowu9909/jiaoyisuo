package com.vaultpi.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kraken 公开 REST API 客户端，用于行情（Ticker）、K 线（OHLC）、盘口（Depth）、成交（Trades）。
 * 美国服务器可用，替代 Binance。连接/读取超时避免生产环境挂死。
 */
@Service
public class KrakenApiClient {

    private static final Logger log = LoggerFactory.getLogger(KrakenApiClient.class);
    private static final String BASE_URL = "https://api.kraken.com/0/public";
    private static final String ASSET_PAIRS_URL = BASE_URL + "/AssetPairs";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** 项目 symbol (BASE/QUOTE) -> Kraken ticker key：硬编码种子（AssetPairs 加载成功后会动态补全） */
    private static final Map<String, String> SEED_SYMBOL_TO_KRAKEN = new LinkedHashMap<>();
    static {
        SEED_SYMBOL_TO_KRAKEN.put("BTC/USDT", "XXBTZUSD");
        SEED_SYMBOL_TO_KRAKEN.put("BTC/USD", "XXBTZUSD");
        SEED_SYMBOL_TO_KRAKEN.put("ETH/USDT", "XETHZUSD");
        SEED_SYMBOL_TO_KRAKEN.put("ETH/USD", "XETHZUSD");
        SEED_SYMBOL_TO_KRAKEN.put("SOL/USDT", "SOLUSD");
        SEED_SYMBOL_TO_KRAKEN.put("XRP/USDT", "XXRPZUSD");
        SEED_SYMBOL_TO_KRAKEN.put("DOGE/USDT", "XDGUSD");
        SEED_SYMBOL_TO_KRAKEN.put("ADA/USDT", "ADAUSD");
        SEED_SYMBOL_TO_KRAKEN.put("AVAX/USDT", "AVAXUSD");
        SEED_SYMBOL_TO_KRAKEN.put("LINK/USDT", "LINKUSD");
        SEED_SYMBOL_TO_KRAKEN.put("DOT/USDT", "DOTUSD");
        SEED_SYMBOL_TO_KRAKEN.put("MATIC/USDT", "MATICUSD");
        SEED_SYMBOL_TO_KRAKEN.put("LTC/USDT", "XLTCZUSD");
        SEED_SYMBOL_TO_KRAKEN.put("BCH/USDT", "BCHUSD");
        SEED_SYMBOL_TO_KRAKEN.put("UNI/USDT", "UNIUSD");
        SEED_SYMBOL_TO_KRAKEN.put("ATOM/USDT", "ATOMUSD");
    }

    private final Map<String, String> symbolToKrakenTickerKey = new LinkedHashMap<>(SEED_SYMBOL_TO_KRAKEN);
    private volatile boolean assetPairsLoaded = false;
    private final Object assetPairsLoadLock = new Object();

    /** Kraken AssetPairs 的 wsname base -> 我们的显示 base（用于构建 BASE/USDT 映射） */
    private static final Map<String, String> KRAKEN_WS_BASE_TO_SYMBOL_BASE = Map.ofEntries(
        Map.entry("XBT", "BTC"),
        Map.entry("XDG", "DOGE")
    );

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /** 将项目内 symbol（如 BTC/USDT）转为 Kraken pair（如 XXBTZUSD） */
    public String toKrakenPair(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        ensureAssetPairsLoaded();
        String normalized;
        if (symbol.contains("/")) {
            normalized = symbol;
        } else {
            // Support both "BASE-QUOTE" (e.g. ETH-USDT) and "BASEQUOTE" suffix forms.
            // The previous regex approach turns "ETH-USDT" into "ETH-/USDT" which breaks mappings.
            int dashIdx = symbol.lastIndexOf('-');
            if (dashIdx > 0 && dashIdx < symbol.length() - 1) {
                String base = symbol.substring(0, dashIdx);
                String quote = symbol.substring(dashIdx + 1);
                if ("USDT".equalsIgnoreCase(quote) || "USD".equalsIgnoreCase(quote)) {
                    normalized = base + "/" + quote;
                } else {
                    normalized = symbol.replaceFirst("(.+)(USDT|USD)$", "$1/$2");
                }
            } else {
                normalized = symbol.replaceFirst("(.+)(USDT|USD)$", "$1/$2");
            }
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.endsWith("/USD")) normalized = normalized.replace("/USD", "/USDT");
        String pair = symbolToKrakenTickerKey.get(normalized);
        if (pair != null) return pair;
        String noSlash = symbol.replace("/", "");
        return symbolToKrakenTickerKey.entrySet().stream()
            .filter(e -> e.getKey().replace("/", "").equalsIgnoreCase(noSlash))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private void ensureAssetPairsLoaded() {
        if (assetPairsLoaded) return;
        synchronized (assetPairsLoadLock) {
            if (assetPairsLoaded) return;
            try {
                String json = restTemplate.getForObject(ASSET_PAIRS_URL, String.class);
                if (json == null || json.isBlank()) return;
                Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                Object resultObj = root.get("result");
                if (!(resultObj instanceof Map<?, ?> result)) return;

                for (Map.Entry<?, ?> entry : result.entrySet()) {
                    String tickerKey = String.valueOf(entry.getKey());
                    Object vObj = entry.getValue();
                    if (!(vObj instanceof Map<?, ?> v)) continue;

                    String quote = (String) v.get("quote");
                    if (quote == null || (!"USD".equals(quote) && !"ZUSD".equals(quote))) continue;

                    String wsname = (String) v.get("wsname"); // e.g. XBT/USD, ETH/USD, ADA/USD
                    if (wsname == null || !wsname.contains("/")) continue;
                    String[] parts = wsname.split("/");
                    if (parts.length < 2) continue;
                    String wsBase = parts[0];
                    String wsQuote = parts[1];
                    if (!"USD".equals(wsQuote)) continue;

                    String symbolBase = KRAKEN_WS_BASE_TO_SYMBOL_BASE.getOrDefault(wsBase, wsBase);
                    if (symbolBase == null || symbolBase.isBlank()) continue;
                    String ourSymbol = symbolBase + "/USDT";
                    symbolToKrakenTickerKey.putIfAbsent(ourSymbol, tickerKey);
                }

                assetPairsLoaded = true;
                log.info("Kraken AssetPairs loaded. symbolToKrakenTickerKey size={}", symbolToKrakenTickerKey.size());
            } catch (Exception e) {
                // 不阻断主流程：继续使用种子映射
                log.warn("Kraken AssetPairs load failed, fallback to seed mapping: {}", e.toString());
            }
        }
    }

    /**
     * 拉取所有 Ticker（Kraken 返回 result 下多个 pair）。返回 map: 项目 symbol -> ticker 数据（open, high, low, close, volume, chg 等）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> fetchAllTickers() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        try {
            ensureAssetPairsLoaded();
            String json = restTemplate.getForObject(BASE_URL + "/Ticker", String.class);
            if (json == null) return out;
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<String> err = (List<String>) root.get("error");
            if (err != null && !err.isEmpty()) return out;
            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return out;

            Map<String, String> krakenToSymbol = new HashMap<>();
            for (Map.Entry<String, String> e : symbolToKrakenTickerKey.entrySet()) {
                String tickerKey = e.getValue();
                String symbolKey = e.getKey();
                String existing = krakenToSymbol.get(tickerKey);
                // 如果同一个 tickerKey 同时映射到了 /USD 和 /USDT，优先使用 /USDT 以匹配项目存储格式
                if (existing == null || (existing.endsWith("/USD") && symbolKey.endsWith("/USDT"))) {
                    krakenToSymbol.put(tickerKey, symbolKey);
                }
            }

            for (Map.Entry<String, Object> entry : result.entrySet()) {
                String krakenPair = entry.getKey();
                String ourSymbol = krakenToSymbol.get(krakenPair);
                if (ourSymbol == null) continue;
                Map<String, Object> t = (Map<String, Object>) entry.getValue();
                if (t == null) continue;
                Map<String, Object> thumb = parseTickerToThumb(t);
                if (thumb != null) out.put(ourSymbol, thumb);
            }
        } catch (Exception e) {
            log.warn("Kraken REST fetchAllTickers failed: {}", e.getMessage());
        }
        return out;
    }

    /** 单个 pair 的 Ticker，用于当前价等 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchTicker(String krakenPair) {
        try {
            String json = restTemplate.getForObject(BASE_URL + "/Ticker?pair=" + krakenPair, String.class);
            if (json == null) return null;
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<String> err = (List<String>) root.get("error");
            if (err != null && !err.isEmpty()) return null;
            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return null;
            Object val = result.get(krakenPair);
            if (val == null && result.size() == 1) val = result.values().iterator().next();
            return val instanceof Map ? (Map<String, Object>) val : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 将项目内 symbol（如 BTC/USDT）直接拉取并解析为 thumb（open/high/low/close/volume 等） */
    public Map<String, Object> fetchThumbForSymbol(String symbol) {
        String krakenPair = toKrakenPair(symbol);
        if (krakenPair == null) return null;
        Map<String, Object> ticker = fetchTicker(krakenPair);
        if (ticker == null) return null;
        return parseTickerToThumb(ticker);
    }

    /** 从 Kraken ticker 解析为 thumb：open, high, low, close, volume, turnover, chg, change */
    private Map<String, Object> parseTickerToThumb(Map<String, Object> t) {
        try {
            BigDecimal open = strToBd(t.get("o"));
            Object cObj = t.get("c");
            BigDecimal close = cObj instanceof List ? strToBd(((List<?>) cObj).get(0)) : strToBd(cObj);
            Object hObj = t.get("h");
            BigDecimal high = hObj instanceof List ? strToBd(((List<?>) hObj).get(0)) : strToBd(hObj);
            Object lObj = t.get("l");
            BigDecimal low = lObj instanceof List ? strToBd(((List<?>) lObj).get(0)) : strToBd(lObj);
            Object vObj = t.get("v");
            BigDecimal volume = vObj instanceof List ? strToBd(((List<?>) vObj).get(0)) : strToBd(vObj);
            BigDecimal change = close.subtract(open);
            BigDecimal chg = open.compareTo(BigDecimal.ZERO) != 0
                ? change.divide(open, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            BigDecimal turnover = volume.multiply(close);

            Map<String, Object> m = new HashMap<>();
            m.put("open", open);
            m.put("high", high);
            m.put("low", low);
            m.put("close", close);
            m.put("volume", volume);
            m.put("turnover", turnover);
            m.put("change", change);
            m.put("chg", chg);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前价（last）：从 Ticker 的 c 字段取 */
    public BigDecimal fetchCurrentPrice(String symbol) {
        String pair = toKrakenPair(symbol);
        if (pair == null) return BigDecimal.ZERO;
        Map<String, Object> t = fetchTicker(pair);
        if (t == null) return BigDecimal.ZERO;
        Object c = t.get("c");
        if (c instanceof List && !((List<?>) c).isEmpty()) {
            return strToBd(((List<?>) c).get(0));
        }
        return strToBd(c);
    }

    /**
     * OHLC：interval 分钟 1,5,15,30,60,240,1440。返回 List<Map time, open, high, low, close, volume>
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchOhlc(String krakenPair, int intervalMinutes, Integer since, int limit) {
        try {
            String url = BASE_URL + "/OHLC?pair=" + krakenPair + "&interval=" + intervalMinutes;
            if (since != null && since > 0) url += "&since=" + since;
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return Collections.emptyList();
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<String> err = (List<String>) root.get("error");
            if (err != null && !err.isEmpty()) return Collections.emptyList();
            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return Collections.emptyList();
            Object pairData = result.get(krakenPair);
            if (pairData == null && result.size() == 1) pairData = result.values().iterator().next();
            if (!(pairData instanceof List)) return Collections.emptyList();
            List<List<?>> rows = (List<List<?>>) pairData;
            List<Map<String, Object>> list = new ArrayList<>();
            int take = Math.min(limit, rows.size());
            for (int i = Math.max(0, rows.size() - take); i < rows.size(); i++) {
                List<?> r = rows.get(i);
                if (r.size() < 7) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("time", ((Number) r.get(0)).longValue() * 1000);
                m.put("open", new BigDecimal(r.get(1).toString()));
                m.put("high", new BigDecimal(r.get(2).toString()));
                m.put("low", new BigDecimal(r.get(3).toString()));
                m.put("close", new BigDecimal(r.get(4).toString()));
                m.put("volume", new BigDecimal(r.get(6).toString()));
                list.add(m);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 盘口 Depth：asks/bids 为 [price, volume, timestamp]，取 price+amount */
    @SuppressWarnings("unchecked")
    public Map<String, List<List<Object>>> fetchDepth(String krakenPair, int count) {
        try {
            String json = restTemplate.getForObject(BASE_URL + "/Depth?pair=" + krakenPair + "&count=" + count, String.class);
            if (json == null) return null;
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<String> err = (List<String>) root.get("error");
            if (err != null && !err.isEmpty()) return null;
            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return null;
            Object pairData = result.get(krakenPair);
            if (pairData == null && result.size() == 1) pairData = result.values().iterator().next();
            if (!(pairData instanceof Map)) return null;
            Map<String, Object> book = (Map<String, Object>) pairData;
            List<List<?>> asksRaw = (List<List<?>>) book.get("asks");
            List<List<?>> bidsRaw = (List<List<?>>) book.get("bids");
            List<List<Object>> asks = asksRaw == null ? new ArrayList<>() : asksRaw.stream()
                .map(row -> Arrays.asList(row.get(0), row.get(1)))
                .collect(Collectors.toList());
            List<List<Object>> bids = bidsRaw == null ? new ArrayList<>() : bidsRaw.stream()
                .map(row -> Arrays.asList(row.get(0), row.get(1)))
                .collect(Collectors.toList());
            Map<String, List<List<Object>>> out = new HashMap<>();
            out.put("asks", asks);
            out.put("bids", bids);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 最近成交 Trades。Kraken: [price, volume, time, side, orderType, misc] */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchTrades(String krakenPair, Long since, int count) {
        try {
            String url = BASE_URL + "/Trades?pair=" + krakenPair;
            if (since != null && since > 0) url += "&since=" + since;
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return Collections.emptyList();
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<String> err = (List<String>) root.get("error");
            if (err != null && !err.isEmpty()) return Collections.emptyList();
            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return Collections.emptyList();
            Object pairData = result.get(krakenPair);
            if (pairData == null && result.size() == 1) pairData = result.values().iterator().next();
            if (!(pairData instanceof List)) return Collections.emptyList();
            List<List<?>> rows = (List<List<?>>) pairData;
            List<Map<String, Object>> list = new ArrayList<>();
            int take = Math.min(count, rows.size());
            for (int i = Math.max(0, rows.size() - take); i < rows.size(); i++) {
                List<?> r = rows.get(i);
                if (r.size() < 4) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("price", new BigDecimal(r.get(0).toString()));
                m.put("amount", new BigDecimal(r.get(1).toString()));
                m.put("time", ((Number) r.get(2)).longValue() * 1000);
                String side = "b".equalsIgnoreCase(r.get(3).toString()) ? "BUY" : "SELL";
                m.put("direction", side);
                list.add(m);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 将 interval 字符串（1m,5m,15m,1h,4h,1d）转为 Kraken 分钟数 */
    public static int intervalToMinutes(String interval) {
        if (interval == null) return 60;
        switch (interval.toLowerCase()) {
            case "1m": return 1;
            case "5m": return 5;
            case "15m": return 15;
            case "30m": return 30;
            case "1h": return 60;
            case "4h": return 240;
            case "1d": return 1440;
            default: return 60;
        }
    }

    private static BigDecimal strToBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
