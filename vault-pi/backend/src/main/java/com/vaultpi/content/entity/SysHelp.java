package com.vaultpi.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "sys_help")
@Data
public class SysHelp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String title;

    /** 分类：新手入门、充值指南、交易指南等 */
    @Column(nullable = false, length = 32)
    private String classification = "OTHER";

    @Column(length = 256)
    private String imgUrl = "";

    @Column(columnDefinition = "text")
    private String content = "";

    @Column(length = 32)
    private String author = "admin";

    private int sort = 0;

    /** 是否置顶：0 置顶，1 不置顶 */
    @Column(length = 4)
    private String isTop = "1";

    @Column(length = 8)
    private String lang = "CN";

    /** NORMAL / DISABLED */
    @Column(nullable = false, length = 16)
    private String status = "NORMAL";

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
