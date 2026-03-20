package com.vaultpi.market.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 虚拟盘趋势设置审计：记录谁、何时、对哪个交易对设置了什么趋势，便于纠纷追溯与合规。
 */
@Entity
@Table(name = "virtual_trend_audit", indexes = {
    @Index(name = "idx_virtual_trend_audit_symbol", columnList = "symbol"),
    @Index(name = "idx_virtual_trend_audit_time", columnList = "operation_time"),
    @Index(name = "idx_virtual_trend_audit_admin", columnList = "admin_id")
})
@Data
public class VirtualTrendAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal percent;

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "operation_time", nullable = false)
    private Long operationTime;

    @Column(length = 64)
    private String ip;

    @Column(name = "start_price", precision = 24, scale = 8)
    private BigDecimal startPrice;
}
