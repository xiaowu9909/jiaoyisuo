package com.vaultpi.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Kraken 行情 Redis 缓存：WS 写入，API/推送读取。
 * Key: MARKET:PRICE:[Symbol], MARKET:PLATE:[Symbol], MARKET:OHLC:[Symbol]:[interval]
 * TTL 5 秒，超时视为行情异常。
 * Bean 由 com.vaultpi.config.MarketRedisConfig 在存在 StringRedisTemplate 时显式注册，避免扫描/环境导致漏注册。
 */
public class KrakenMarketRedisService {

    private static final String KEY_PREFIX_PRICE = "MARKET:PRICE:";
    private static final String KEY_PREFIX_PLATE = "MARKET:PLATE:";
    private static final String KEY_PREFIX_OHLC = "MARKET:OHLC:";
    /** 行情最后更新时间戳（用于熔断检测：超过 10s 未更新则告警） */
    public static final String KEY_LAST_UPDATE = "MARKET:LAST_UPDATE";
    private static final long TTL_SECONDS = 5L;
    private static final long LAST_UPDATE_TTL_SECONDS = 15L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KrakenMarketRedisService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 写入 ticker 拇指数据（open, high, low, close, volume, turnover, chg, change） */
    public void putThumb(String symbol, Map<String, Object> thumb) {
        if (symbol == null || thumb == null) return;
        try {
            String key = KEY_PREFIX_PRICE + symbol;
            String json = objectMapper.writeValueAsString(thumb);
            redis.opsForValue().set(key, json, TTL_SECONDS, TimeUnit.SECONDS);
            redis.opsForValue().set(KEY_LAST_UPDATE, String.valueOf(System.currentTimeMillis()), LAST_UPDATE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }
    }

    /** 获取行情最后更新时间（毫秒），不存在或异常返回 0 */
    public long getLastUpdateTimeMillis() {
        try {
            String v = redis.opsForValue().get(KEY_LAST_UPDATE);
            return v != null ? Long.parseLong(v) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 读取单个 symbol 的 thumb，不存在返回 null */
    public Map<String, Object> getThumb(String symbol) {
        if (symbol == null) return null;
        try {
            String key = KEY_PREFIX_PRICE + symbol;
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取所有已缓存的 thumb（仅限 key 匹配 MARKET:PRICE:*） */
    public Map<String, Map<String, Object>> getAllThumbs() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        try {
            Set<String> keys = redis.keys(KEY_PREFIX_PRICE + "*");
            if (keys == null) return out;
            for (String key : keys) {
                String symbol = key.substring(KEY_PREFIX_PRICE.length());
                String json = redis.opsForValue().get(key);
                if (json == null || json.isBlank()) continue;
                try {
                    out.put(symbol, objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // ignore
        }
        return out;
    }

    /** 写入盘口 ask/bid 列表 */
    public void putPlate(String symbol, List<Map<String, Object>> ask, List<Map<String, Object>> bid) {
        if (symbol == null) return;
        try {
            String key = KEY_PREFIX_PLATE + symbol;
            Map<String, Object> payload = new HashMap<>();
            payload.put("ask", ask != null ? ask : List.of());
            payload.put("bid", bid != null ? bid : List.of());
            String json = objectMapper.writeValueAsString(payload);
            redis.opsForValue().set(key, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }
    }

    /** 读取盘口 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlate(String symbol) {
        if (symbol == null) return null;
        try {
            String key = KEY_PREFIX_PLATE + symbol;
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ask", raw.getOrDefault("ask", List.of()));
            out.put("bid", raw.getOrDefault("bid", List.of()));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 写入最后一根 K 线（OHLC 单条） */
    public void putOhlcLast(String symbol, int intervalMinutes, Map<String, Object> bar) {
        if (symbol == null || bar == null) return;
        try {
            String key = KEY_PREFIX_OHLC + symbol + ":" + intervalMinutes;
            String json = objectMapper.writeValueAsString(bar);
            redis.opsForValue().set(key, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }
    }

    /** 读取最后一根 K 线 */
    public Map<String, Object> getOhlcLast(String symbol, int intervalMinutes) {
        if (symbol == null) return null;
        try {
            String key = KEY_PREFIX_OHLC + symbol + ":" + intervalMinutes;
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** 将 Map 中 Number 转为 BigDecimal（用于 thumb 一致性） */
    public static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
