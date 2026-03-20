package com.vaultpi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 为 WebSocket 简单代理提供 TaskScheduler，用于心跳。
 * 配置了 setHeartbeatValue 时必须提供，否则会报 Heartbeat values configured but no TaskScheduler provided。
 */
@Configuration
public class WebSocketSchedulerConfig {

    @Bean(name = "webSocketHeartbeatScheduler")
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
