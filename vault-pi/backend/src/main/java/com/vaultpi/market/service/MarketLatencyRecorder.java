package com.vaultpi.market.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 行情推送全链路延迟：Kraken 消息 -> Redis -> 推送至前端。
 * 定期输出日志，确保延迟低于 1000ms。
 */
@Slf4j
@Component
public class MarketLatencyRecorder {

    private final Timer krakenToRedisTimer;
    private final Timer redisToPushTimer;
    private final AtomicLong lastKrakenToRedisNanos = new AtomicLong(0);
    private final AtomicLong lastRedisToPushNanos = new AtomicLong(0);

    public MarketLatencyRecorder(MeterRegistry registry) {
        this.krakenToRedisTimer = Timer.builder("vaultpi.market.latency")
            .tag("stage", "kraken_to_redis")
            .register(registry);
        this.redisToPushTimer = Timer.builder("vaultpi.market.latency")
            .tag("stage", "redis_to_push")
            .register(registry);
    }

    public void recordKrakenToRedis(long nanos) {
        krakenToRedisTimer.record(nanos, TimeUnit.NANOSECONDS);
        lastKrakenToRedisNanos.set(nanos);
    }

    public void recordRedisToPush(long nanos) {
        redisToPushTimer.record(nanos, TimeUnit.NANOSECONDS);
        lastRedisToPushNanos.set(nanos);
    }

    @Scheduled(fixedRate = 60_000)
    public void logLatency() {
        long k2r = lastKrakenToRedisNanos.get();
        long r2p = lastRedisToPushNanos.get();
        if (k2r == 0 && r2p == 0) return;
        double k2rMs = k2r / 1_000_000.0;
        double r2pMs = r2p / 1_000_000.0;
        double totalMs = k2rMs + r2pMs;
        if (totalMs > 1000) {
            log.warn("Market push latency HIGH: kraken_to_redis={}ms, redis_to_push={}ms, total≈{}ms (threshold 1000ms)", k2rMs, r2pMs, totalMs);
        } else {
            log.info("Market push latency: kraken_to_redis={}ms, redis_to_push={}ms", k2rMs, r2pMs);
        }
    }
}
