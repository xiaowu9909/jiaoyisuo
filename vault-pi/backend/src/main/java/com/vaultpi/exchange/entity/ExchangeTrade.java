package com.vaultpi.exchange.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** 成交记录，用于最新成交列表与 K 线汇总 */
@Entity
@Table(name = "exchange_trade", indexes = {
    @Index(name = "idx_exchange_trade_symbol_time", columnList = "symbol, create_time")
})
@Data
public class ExchangeTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal price;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal amount;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
