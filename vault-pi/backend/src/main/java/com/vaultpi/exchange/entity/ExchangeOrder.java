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

    /** 是否AI生成订单：0=否, 1=是 */
    @Column(name = "is_ai", nullable = false)
    private Integer isAi = 0;

    /** 是否错过单（余额不足）：0=否, 1=是 */
    @Column(name = "is_missed", nullable = false)
    private Integer isMissed = 0;

    /** 错过单时记录所需最低余额 */
    @Column(name = "required_balance", precision = 18, scale = 8)
    private BigDecimal requiredBalance;

    /** AI执行理由话术 */
    @Column(name = "ai_note", length = 255)
    private String aiNote;

    /** 盈亏金额（AI单使用） */
    @Column(precision = 18, scale = 8)
    private BigDecimal profit;

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
