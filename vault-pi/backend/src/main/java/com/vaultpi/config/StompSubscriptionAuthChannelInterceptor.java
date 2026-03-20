package com.vaultpi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限制 STOMP SUBSCRIBE 目的地必须属于允许列表，并且要求握手阶段存在已登录用户身份。
 * 目的：防止未登录/任意用户订阅所有行情推送通道，造成信息泄露与资源消耗。
 */
public class StompSubscriptionAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompSubscriptionAuthChannelInterceptor.class);

    private static final Set<String> ALLOWED_TOPICS = Set.of(
        "/topic/virtual-prices",
        "/topic/market-thumb",
        "/topic/market-status",
        "/topic/trades"
    );

    /** 详情页按交易对维度的行情推送：/topic/market-pair-thumb/{PAIR_KEY} */
    public static final String PAIR_THUMB_TOPIC_PREFIX = "/topic/market-pair-thumb/";

    /** 记录最近一次订阅某个 pair topic 的时间戳（TTL 用于 MarketStreamTask 降载） */
    private static final ConcurrentHashMap<String, Long> PAIR_LAST_SUBSCRIBE_AT = new ConcurrentHashMap<>();
    // 10s 会导致详情页在订阅后很快“失活”，后端停止对该 pair 推送，前端只能长期 fallback 轮询。
    // 提高到 2 分钟，保证正常停留详情页期间持续收到 WS 推送。
    private static final long PAIR_TOPIC_ACTIVE_TTL_MS = 120_000;

    /**
     * 需要强制登录的 topic 集合。
     * 当前项目里，行情类 topic 对体验更敏感，且前端未登录状态也会订阅；
     * 因此只对用户相关的 trades 强制鉴权，避免未登录行情长时间不更新。
     */
    private static final Set<String> AUTH_REQUIRED_TOPICS = Set.of(
        "/topic/trades"
    );

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand cmd = accessor.getCommand();
        if (cmd == null) return message;

        // 仅在 SUBSCRIBE 阶段做鉴权：允许 CONNECT 让客户端建立连接，避免握手阶段处理过多逻辑
        if (StompCommand.SUBSCRIBE.equals(cmd)) {
            String destination = accessor.getDestination();
            if (destination == null || destination.isBlank()) {
                throw new MessagingException("缺少 destination，禁止订阅");
            }

            boolean allowed = ALLOWED_TOPICS.contains(destination) || destination.startsWith(PAIR_THUMB_TOPIC_PREFIX);
            if (!allowed) {
                throw new MessagingException("非法订阅目标 topic: " + destination);
            }

            if (destination.startsWith(PAIR_THUMB_TOPIC_PREFIX)) {
                PAIR_LAST_SUBSCRIBE_AT.put(destination, System.currentTimeMillis());
            }

            if (AUTH_REQUIRED_TOPICS.contains(destination)) {
                Long memberId = extractMemberId(accessor);
                if (memberId == null) {
                    log.warn("WS SUBSCRIBE denied: not authenticated, destination={}", destination);
                    throw new MessagingException("请先登录后再订阅该内容");
                }
            }
        }

        return message;
    }

    private static Long extractMemberId(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null || attrs.isEmpty()) return null;
        Object v = attrs.get(RequireLoginInterceptor.SESSION_MEMBER_ID);
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    /** Pair topic 是否处于“活跃订阅”窗口内 */
    public static boolean isPairTopicActive(String destination) {
        if (destination == null || destination.isBlank()) return false;
        Long at = PAIR_LAST_SUBSCRIBE_AT.get(destination);
        if (at == null) return false;
        return (System.currentTimeMillis() - at) <= PAIR_TOPIC_ACTIVE_TTL_MS;
    }

    /** 获取当前活跃 pair topic 的集合（用于高频详情页精准推送） */
    public static Set<String> getActivePairKeys() {
        try {
            if (PAIR_LAST_SUBSCRIBE_AT.isEmpty()) return Set.of();
            long now = System.currentTimeMillis();
            Set<String> out = new java.util.HashSet<>();
            for (Map.Entry<String, Long> e : PAIR_LAST_SUBSCRIBE_AT.entrySet()) {
                String dest = e.getKey();
                Long at = e.getValue();
                if (dest == null || at == null) continue;
                if ((now - at) > PAIR_TOPIC_ACTIVE_TTL_MS) {
                    // 过期键顺手清理，避免 map 无界增长
                    PAIR_LAST_SUBSCRIBE_AT.remove(dest, at);
                    continue;
                }
                if (!dest.startsWith(PAIR_THUMB_TOPIC_PREFIX)) continue;
                String key = dest.substring(PAIR_THUMB_TOPIC_PREFIX.length());
                if (!key.isBlank()) out.add(key);
            }
            return out;
        } catch (Exception e) {
            return Set.of();
        }
    }
}

