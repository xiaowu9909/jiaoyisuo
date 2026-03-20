package com.vaultpi.exchange.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 幂等键校验：同一 key 在有效期内仅允许一次成功执行，防止重复下单等。
 * Key 来源：请求头 X-Idempotency-Key 或 body 的 idempotencyKey。
 * 无 Redis 时不创建本 Bean，OrderController 通过可选注入处理。
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class IdempotencyService {

    private static final String PREFIX = "idempotency:order:";
    private static final String PROCESSING = "processing";
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(60);
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 若 key 为空则无需幂等校验，返回 null。否则检查是否已处理或正在处理。
     * @return 已存在的 orderId，或 "processing" 表示正在处理，或 null 表示可继续执行
     */
    public String checkAndMarkProcessing(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        try {
            String key = PREFIX + idempotencyKey.trim();
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) return existing;
            Boolean set = redisTemplate.opsForValue().setIfAbsent(key, PROCESSING, PROCESSING_TTL);
            return Boolean.TRUE.equals(set) ? null : redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 执行成功后，将 key 对应值设为 orderId，延长 TTL。
     */
    public void setResult(String idempotencyKey, String orderId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        try {
            String key = PREFIX + idempotencyKey.trim();
            redisTemplate.opsForValue().set(key, orderId, RESULT_TTL);
        } catch (Exception ignored) {
        }
    }
}
