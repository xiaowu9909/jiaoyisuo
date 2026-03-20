package com.vaultpi.market.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 启动时连接 Kraken WebSocket，并对外暴露连接状态（供 REST 降级判断）。
 */
@Slf4j
@Component
@ConditionalOnBean(KrakenMarketRedisService.class)
public class KrakenWebSocketRunner {

    private static final String WS_URL = "wss://ws.kraken.com/v2";

    private final KrakenWebSocketClient client;

    public KrakenWebSocketRunner(KrakenMarketRedisService redisService,
                                 @Qualifier("webSocketHeartbeatScheduler") org.springframework.scheduling.TaskScheduler taskScheduler,
                                 @Value("${vaultpi.kraken.ws.reconnect-delay-ms:2000}") long reconnectDelayMs,
                                 @Autowired(required = false) MarketLatencyRecorder latencyRecorder) {
        this.client = new KrakenWebSocketClient(
            URI.create(WS_URL), redisService, taskScheduler, reconnectDelayMs, latencyRecorder);
        try {
            client.connect();
        } catch (Exception e) {
            log.error("Kraken WebSocket connect failed: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @PreDestroy
    public void destroy() {
        if (client != null) client.destroy();
    }
}
