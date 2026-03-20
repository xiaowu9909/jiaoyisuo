package com.vaultpi.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 为 SockJS/STOMP 握手阶段注入用户身份（从 HttpSession 读取 memberId）。
 * 后续 STOMP 订阅时由 ChannelInterceptor 做 topic 授权。
 */
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        try {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                HttpServletRequest http = servletRequest.getServletRequest();
                HttpSession session = http.getSession(false);
                Long memberId = null;
                if (session != null) {
                    Object v = session.getAttribute(RequireLoginInterceptor.SESSION_MEMBER_ID);
                    if (v instanceof Number n) memberId = n.longValue();
                }
                if (memberId != null) {
                    attributes.put(RequireLoginInterceptor.SESSION_MEMBER_ID, memberId);
                }
                attributes.put("wsAuthenticated", memberId != null);
            }
        } catch (Exception e) {
            // 握手不阻断连接；由后续 SUBSCRIBE 鉴权拦截决定是否拒绝订阅
            log.debug("WS handshake auth attribute extract failed: {}", e.toString());
        }
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // no-op
    }
}

