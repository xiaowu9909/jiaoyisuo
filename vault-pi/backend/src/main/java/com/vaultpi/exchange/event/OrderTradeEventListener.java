package com.vaultpi.exchange.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单成交通知：事务提交后才发布；日志 + WebSocket 推送，前端订阅 /topic/trades 后可根据 memberId 展示 toast。
 */
@Slf4j
@Component
public class OrderTradeEventListener {

    public static final String TOPIC_TRADES = "/topic/trades";

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderTrade(OrderTradeEvent event) {
        log.info("Order filled: orderId={}, memberId={}, symbol={}, direction={}, amount={}, price={}",
            event.getOrderId(), event.getMemberId(), event.getSymbol(), event.getDirection(),
            event.getAmount(), event.getPrice());
        if (messagingTemplate != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("memberId", event.getMemberId());
            payload.put("orderId", event.getOrderId());
            payload.put("symbol", event.getSymbol());
            payload.put("direction", event.getDirection());
            payload.put("amount", event.getAmount());
            payload.put("price", event.getPrice());
            messagingTemplate.convertAndSend(TOPIC_TRADES, payload);
        }
    }
}
