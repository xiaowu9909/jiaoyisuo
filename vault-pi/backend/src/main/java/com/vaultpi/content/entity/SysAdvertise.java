package com.vaultpi.content.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "sys_advertise")
@Data
public class SysAdvertise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** 0: 启用, 1: 禁用 */
    @Column(nullable = false)
    private Integer status = 0;

    @Column(length = 16)
    private String lang = "CN";

    @Column(name = "create_time")
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
