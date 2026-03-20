package com.vaultpi.market.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 行情熔断：Redis 行情超过 10 秒未更新时触发告警并向前端推送「行情维护中」。
 */
@Slf4j
@Component
@ConditionalOnBean(KrakenMarketRedisService.class)
public class MarketCircuitBreaker {

    public static final String TOPIC_MARKET_STATUS = "/topic/market-status";
    private static final long STALE_THRESHOLD_MS = 10_000L;

    private final KrakenMarketRedisService krakenMarketRedisService;
    private final SimpMessagingTemplate messagingTemplate;
    private final String webhookUrl;

    private final AtomicBoolean maintenance = new AtomicBoolean(false);

    public MarketCircuitBreaker(KrakenMarketRedisService krakenMarketRedisService,
                                SimpMessagingTemplate messagingTemplate,
                                @Value("${vaultpi.market.circuit-breaker.webhook-url:}") String webhookUrl) {
        this.krakenMarketRedisService = krakenMarketRedisService;
        this.messagingTemplate = messagingTemplate;
        this.webhookUrl = webhookUrl != null && !webhookUrl.isBlank() ? webhookUrl : null;
    }

    public boolean isMaintenance() {
        return maintenance.get();
    }

    @Scheduled(fixedRate = 5000)
    public void checkAndNotify() {
        long last = krakenMarketRedisService.getLastUpdateTimeMillis();
        long now = System.currentTimeMillis();
        boolean stale = (last == 0 || (now - last > STALE_THRESHOLD_MS));

        if (stale && maintenance.compareAndSet(false, true)) {
            log.error("Market circuit breaker OPEN: Redis market data not updated for >10s (lastUpdate={}, now={}). 行情维护中。", last, now);
            notifyWebhook(true);
            pushStatus(true);
        } else if (!stale && maintenance.compareAndSet(true, false)) {
            log.info("Market circuit breaker CLOSED: market data resumed.");
            pushStatus(false);
        } else if (stale) {
            pushStatus(true);
        }
    }

    private void pushStatus(boolean maintenanceNow) {
        try {
            Map<String, Object> payload = Map.of(
                "maintenance", maintenanceNow,
                "message", maintenanceNow ? "行情维护中" : "正常"
            );
            messagingTemplate.convertAndSend(TOPIC_MARKET_STATUS, payload);
        } catch (Exception e) {
            log.trace("Failed to push market status: {}", e.getMessage());
        }
    }

    private void notifyWebhook(boolean open) {
        if (webhookUrl == null) return;
        try {
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            String body = open ? "{\"text\":\"VaultPi 行情熔断：Redis 超过 10 秒无更新，行情维护中\"}" : "{\"text\":\"VaultPi 行情熔断已恢复\"}";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            rt.postForEntity(webhookUrl, new org.springframework.http.HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.warn("Circuit breaker webhook failed: {}", e.getMessage());
        }
    }
}
