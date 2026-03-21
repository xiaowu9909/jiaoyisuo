package com.vaultpi.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "ai_plan")
@Data
public class AiPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer days;

    @Column(nullable = false)
    private Integer status = 1; // 1=上架, 0=下架
}
