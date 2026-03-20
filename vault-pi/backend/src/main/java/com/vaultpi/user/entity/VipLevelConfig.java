package com.vaultpi.user.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员等级配置：VIP0-VIP6，充值金额门槛与对应杠杆倍数
 */
@Entity
@Table(name = "vip_level_config", uniqueConstraints = @UniqueConstraint(columnNames = "level"))
@Data
public class VipLevelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 等级 0-6，0=默认 */
    @Column(nullable = false, unique = true)
    private Integer level;

    /** 累计充值金额达到此值（含）即晋升为该等级（结算币如 USDT） */
    @Column(name = "recharge_threshold", precision = 24, scale = 8, nullable = false)
    private BigDecimal rechargeThreshold = BigDecimal.ZERO;

    /** 该等级允许的最大杠杆倍数（合约） */
    @Column(name = "leverage_multiplier", nullable = false)
    private Integer leverageMultiplier = 5;
}
