package com.vaultpi.config;

import com.vaultpi.market.service.KrakenWebSocketRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * /actuator/health 中 krakenWs 组件：Kraken WebSocket 连接状态。
 * KrakenWebSocketRunner 在无 Redis 时不会创建，故此处可选注入，避免启动报错。
 */
@Component
public class KrakenWsHealthIndicator implements HealthIndicator {

    private final KrakenWebSocketRunner krakenWebSocketRunner;

    public KrakenWsHealthIndicator(@Autowired(required = false) KrakenWebSocketRunner krakenWebSocketRunner) {
        this.krakenWebSocketRunner = krakenWebSocketRunner;
    }

    @Override
    public Health health() {
        if (krakenWebSocketRunner == null) {
            return Health.unknown().withDetail("krakenWs", "NOT_AVAILABLE").build();
        }
        boolean connected = krakenWebSocketRunner.isConnected();
        return connected
            ? Health.up().withDetail("krakenWs", "CONNECTED").build()
            : Health.down().withDetail("krakenWs", "DISCONNECTED").build();
    }
}
