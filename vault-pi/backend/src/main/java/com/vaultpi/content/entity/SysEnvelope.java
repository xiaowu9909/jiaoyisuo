package com.vaultpi.content.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sys_envelope")
@Data
public class SysEnvelope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Integer totalCount;

    @Column(nullable = false, precision = 26, scale = 16)
    private BigDecimal remainingAmount;

    @Column(nullable = false)
    private Integer remainingCount;

    @Column(length = 16)
    private String symbol = "USDT";

    /** 0: 随机红包, 1: 定额红包 */
    @Column(nullable = false)
    private Integer type = 0;

    /** 0: 领取中, 1: 已领完, 2: 已过期 */
    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "create_time")
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
