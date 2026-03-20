package com.vaultpi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 基于 Redis + Lua 的令牌桶限流：按 IP（登录/注册）或 IP/用户ID（下单）。
 * 配置：vaultpi.ratelimit.enabled、*.per-min；默认登录 60/分钟、注册 5/分钟、下单 30/分钟。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_PREFIX = "ratelimit:";
    private static final String SESSION_MEMBER_ID = "memberId";

    private final boolean enabled;
    private final int loginPerMin;
    private final int registerPerMin;
    private final int orderPerMin;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

    public RateLimitFilter(boolean enabled, int loginPerMin, int registerPerMin, int orderPerMin,
                           StringRedisTemplate redisTemplate) {
        this.enabled = enabled;
        this.loginPerMin = loginPerMin;
        this.registerPerMin = registerPerMin;
        this.orderPerMin = orderPerMin;
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setLocation(new ClassPathResource("META-INF/scripts/ratelimit-token-bucket.lua"));
        this.tokenBucketScript.setResultType(Long.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!enabled || !uri.startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = uri.startsWith("/api/v1/") || "/api/v1".equals(uri) ? "/api" + (uri.length() > 8 ? uri.substring(8) : "") : uri;
        String ip = clientIp(request);
        String key;
        int capacityAndRate;
        if ("/api/login".equals(path)) {
            key = RATE_PREFIX + "login:" + ip;
            capacityAndRate = loginPerMin;
        } else if ("/api/register".equals(path)) {
            key = RATE_PREFIX + "register:" + ip;
            capacityAndRate = registerPerMin;
        } else if ("/api/order/add".equals(path) || "/api/futures/order/add".equals(path)) {
            Long memberId = sessionMemberId(request);
            key = RATE_PREFIX + "order:" + (memberId != null ? "m" + memberId : ip);
            capacityAndRate = orderPerMin;
        } else {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            long nowSec = System.currentTimeMillis() / 1000;
            Long allowed = redisTemplate.execute(tokenBucketScript, List.of(key),
                String.valueOf(nowSec), String.valueOf(capacityAndRate), String.valueOf(capacityAndRate));
            if (allowed != null && allowed == 0) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"code\":5002,\"message\":\"操作过于频繁，请 60 秒后重试\"}");
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时放行，避免阻塞业务
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private Long sessionMemberId(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s == null) return null;
        Object v = s.getAttribute(SESSION_MEMBER_ID);
        return v instanceof Long ? (Long) v : null;
    }
}
