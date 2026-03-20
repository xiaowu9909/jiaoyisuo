package com.vaultpi.futures.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "contract_order", indexes = {
    @Index(name = "idx_contract_order_symbol_time", columnList = "symbol, create_time")
})
@Data
public class ContractOrder {
    @Id
    @Column(length = 64)
    private String orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 8) // LONG / SHORT
    private String direction;

    @Column(nullable = false, length = 16) // LIMIT / MARKET
    private String type;

    @Column(nullable = false)
    private Integer leverage = 20;

    @Column(precision = 26, scale = 16)
    private BigDecimal price = BigDecimal.ZERO; 

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal margin = BigDecimal.ZERO; // margin in USDT held for this order

    @Column(nullable = false, length = 16) // PENDING, CANCELED, COMPLETED
    private String status = "PENDING";

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
