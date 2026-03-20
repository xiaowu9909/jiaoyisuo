package com.vaultpi.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_audit_action_created", columnList = "action, created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 512)
    private String detail;

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static final String ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String ACTION_LOGIN_FAIL = "LOGIN_FAIL";
    public static final String ACTION_PASSWORD_UPDATE = "PASSWORD_UPDATE";
    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";
}
