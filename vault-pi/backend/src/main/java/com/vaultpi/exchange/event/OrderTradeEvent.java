package com.vaultpi.exchange.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * 订单成交事件，用于异步通知（日志、邮件、推送等）。
 */
@Getter
public class OrderTradeEvent extends ApplicationEvent {

    private final String orderId;
    private final Long memberId;
    private final String symbol;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final String direction;

    public OrderTradeEvent(Object source, String orderId, Long memberId, String symbol,
                           BigDecimal amount, BigDecimal price, String direction) {
        super(source);
        this.orderId = orderId;
        this.memberId = memberId;
        this.symbol = symbol;
        this.amount = amount;
        this.price = price;
        this.direction = direction;
    }
}
