package com.vaultpi.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * K 线结果缓存，TTL 60 秒。无 Redis 时不创建（dev 可不用 Redis）。
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class KlineCacheService {

    private static final String PREFIX = "kline:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KlineCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<Map<String, Object>> get(String symbol, String interval, int limit, Long endTime) {
        String key = key(symbol, interval, limit, endTime);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) return null;
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public void put(String symbol, String interval, int limit, Long endTime, List<Map<String, Object>> data) {
        if (data == null) return;
        String key = key(symbol, interval, limit, endTime);
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception ignored) {
        }
    }

    private static String key(String symbol, String interval, int limit, Long endTime) {
        return PREFIX + symbol + ":" + interval + ":" + limit + ":" + (endTime != null ? endTime : "0");
    }
}
