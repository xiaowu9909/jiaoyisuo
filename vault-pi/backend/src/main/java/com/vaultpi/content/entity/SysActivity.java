package com.vaultpi.content.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "sys_activity")
@Data
public class SysActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    /** 0: 预热, 1: 进行中, 2: 已结束, 3: 已下线 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "create_time")
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
