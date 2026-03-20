package com.vaultpi.asset.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员钱包（与旧项目 MemberWallet 对应）。
 * version 用于撮合批量更新时的乐观锁，避免资金与订单状态不一致。
 */
@Entity
@Table(name = "member_wallet", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "member_id", "coin_id" })
})
@Data
public class MemberWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coin_id", nullable = false)
    private Long coinId;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
