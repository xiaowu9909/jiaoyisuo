package com.vaultpi.asset.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "withdraw_record")
@Data
public class WithdrawRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coin_id", nullable = false)
    private Long coinId;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(precision = 26, scale = 16)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(precision = 26, scale = 16)
    private BigDecimal arrivedAmount = BigDecimal.ZERO;

    @Column(length = 128)
    private String address;

    /** PROCESSING, APPROVED, REJECTED */
    @Column(nullable = false, length = 16)
    private String status = "PROCESSING";

    @Column(length = 64)
    private String transactionNumber;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @Column(length = 255)
    private String remark;

    @Column(name = "deal_time")
    private Instant dealTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
