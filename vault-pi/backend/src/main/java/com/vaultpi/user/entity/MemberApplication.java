package com.vaultpi.user.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "member_application")
@Data
public class MemberApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(length = 64)
    private String realName;

    @Column(length = 32)
    private String idCard;

    @Column(length = 256)
    private String identityCardImgFront;

    @Column(length = 256)
    private String identityCardImgReverse;

    @Column(length = 256)
    private String identityCardImgInHand;

    /** PENDING / APPROVED / REJECTED */
    @Column(nullable = false, length = 16)
    private String auditStatus = "PENDING";

    @Column(length = 256)
    private String rejectReason;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
