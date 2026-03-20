package com.vaultpi.content.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sys_bond")
@Data
public class SysBond {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 16)
    private String symbol = "USDT";

    @Column(length = 255)
    private String remark;

    /** 0: 正常, 1: 已退回, 2: 扣除 */
    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "create_time")
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
