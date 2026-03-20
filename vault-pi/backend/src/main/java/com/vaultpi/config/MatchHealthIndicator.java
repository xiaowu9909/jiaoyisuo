package com.vaultpi.config;

import com.vaultpi.exchange.orderbook.MatchBatchPersistence;
import com.vaultpi.exchange.service.MatchService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 撮合与批量持久化健康检查：订单簿已加载且队列积压未超阈值时 UP，便于 K8s/负载均衡就绪探测。
 */
@Component
@ConditionalOnBean(MatchService.class)
public class MatchHealthIndicator implements HealthIndicator {

    private static final int QUEUE_SIZE_WARN_THRESHOLD = 50_000;

    private final MatchService matchService;
    private final MatchBatchPersistence batchPersistence;

    public MatchHealthIndicator(MatchService matchService,
                                 MatchBatchPersistence batchPersistence) {
        this.matchService = matchService;
        this.batchPersistence = batchPersistence;
    }

    @Override
    public Health health() {
        boolean loaded = matchService.isOrderBooksLoaded();
        int queueSize = batchPersistence.getPendingCount();
        if (!loaded) {
            return Health.down().withDetail("orderBooksLoaded", false).withDetail("reason", "order books not yet loaded").build();
        }
        if (queueSize >= QUEUE_SIZE_WARN_THRESHOLD) {
            return Health.down().withDetail("orderBooksLoaded", true).withDetail("queueSize", queueSize)
                .withDetail("reason", "batch queue backlog too high").build();
        }
        return Health.up().withDetail("orderBooksLoaded", true).withDetail("queueSize", queueSize).build();
    }
}
