package com.vaultpi.asset.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "member_transaction")
@Data
public class MemberTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal amount = BigDecimal.ZERO;

    /** RECHARGE, WITHDRAW, TRADE, TRANSFER, ADMIN_RECHARGE 等 */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 16)
    private String symbol;

    @Column(length = 128)
    private String address;

    @Column(precision = 26, scale = 16)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
