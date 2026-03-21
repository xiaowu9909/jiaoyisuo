package com.vaultpi.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_subscribe_record")
@Data
public class AiSubscribeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal cost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
