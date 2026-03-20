package com.vaultpi.futures.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "contract_position")
@Data
public class ContractPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 8) // LONG / SHORT
    private String direction;

    @Column(nullable = false)
    private Integer leverage = 20;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal avgPrice = BigDecimal.ZERO; // Entry price

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal volume = BigDecimal.ZERO; // position size

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal margin = BigDecimal.ZERO; // isolated margin deposited

    @Column(nullable = false, length = 16) // OPEN, CLOSED, LIQUIDATED
    private String status = "OPEN";
    
    // For closed positions: calculate the final realized PNL
    @Column(precision = 26, scale = 16)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        updateTime = Instant.now();
    }
}
