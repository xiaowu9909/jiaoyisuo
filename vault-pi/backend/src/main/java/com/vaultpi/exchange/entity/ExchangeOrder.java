package com.vaultpi.exchange.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "exchange_order", indexes = {
    @Index(name = "idx_exchange_order_symbol_time", columnList = "symbol, create_time")
})
@Data
public class ExchangeOrder {

    public static final String STATUS_TRADING = "TRADING";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(length = 64)
    private String orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(precision = 26, scale = 16)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal tradedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 16)
    private String status = "TRADING";

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }

    /** 是否仍可交易（未完全成交且未撤单） */
    public boolean isTradable() {
        return STATUS_TRADING.equals(status)
            && amount != null && tradedAmount != null
            && tradedAmount.compareTo(amount) < 0;
    }

    /** 未成交数量 */
    public BigDecimal getRemainingAmount() {
        if (amount == null || tradedAmount == null) return amount != null ? amount : BigDecimal.ZERO;
        BigDecimal remain = amount.subtract(tradedAmount);
        return remain.compareTo(BigDecimal.ZERO) > 0 ? remain : BigDecimal.ZERO;
    }
}
