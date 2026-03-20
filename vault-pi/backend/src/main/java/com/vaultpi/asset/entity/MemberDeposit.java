package com.vaultpi.asset.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** 充币记录 */
@Entity
@Table(name = "member_deposit")
@Data
public class MemberDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coin_id", nullable = false)
    private Long coinId;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 128)
    private String address;

    @Column(length = 128)
    private String txId;

    /** PENDING, CONFIRMED, REJECTED */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    /** 用户提交的转账详情图（data URL base64），人工审核入账时使用 */
    @Column(name = "transfer_image", columnDefinition = "TEXT")
    private String transferImage;

    /** 拒绝原因（仅 status=REJECTED 时有值） */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
