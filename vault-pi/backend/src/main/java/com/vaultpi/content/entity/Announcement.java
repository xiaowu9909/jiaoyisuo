package com.vaultpi.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "announcement")
@Data
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 8)
    private String lang = "CN";

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @Column(name = "is_top", length = 4, nullable = false)
    private String isTop = "1"; // "0"=Yes, "1"=No

    @Column(nullable = false, length = 16)
    private String status = "NORMAL"; // NORMAL, DISABLED

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = Instant.now();
    }
}
