package com.vaultpi.config;

import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebSocket STOMP 配置。生产环境通过 vaultpi.websocket.allowed-origin-patterns 限制来源，禁止使用 *。
 * 配置了心跳时需提供 TaskScheduler，否则会报 Heartbeat values configured but no TaskScheduler provided。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 生产环境应配置为前端域名列表（逗号分隔），如 https://vault314.com,https://admin.vault314.com */
    @Value("${vaultpi.websocket.allowed-origin-patterns:${app.cors.allowed-origins:*}}")
    private String allowedOriginPatternsConfig;

    private final TaskScheduler taskScheduler;

    public WebSocketConfig(@Qualifier("webSocketHeartbeatScheduler") TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
            .setTaskScheduler(taskScheduler)
            .setHeartbeatValue(new long[] { 10000, 10000 });
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 订阅鉴权：确保只有已登录用户能订阅受保护 topic
        registration.interceptors(new StompSubscriptionAuthChannelInterceptor());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> patterns = Arrays.stream(allowedOriginPatternsConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        if (patterns.isEmpty()) patterns.add("*");
        registry.addEndpoint("/ws/virtual-market")
            .setAllowedOriginPatterns(patterns.toArray(new String[0]))
            .addInterceptors(new WebSocketAuthHandshakeInterceptor())
            .withSockJS();
    }
}
