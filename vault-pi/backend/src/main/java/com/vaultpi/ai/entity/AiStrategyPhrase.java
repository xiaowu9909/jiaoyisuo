package com.vaultpi.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "ai_strategy_phrases")
@Data
public class AiStrategyPhrase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer type; // 1=运行状态流, 2=盈利理由, 3=亏损理由, 4=错过理由

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private Integer status = 1; // 1=启用, 0=禁用
}
