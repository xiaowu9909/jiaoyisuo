package com.vaultpi.asset.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 币种（基础表，与旧项目 Coin 对应）
 */
@Entity
@Table(name = "coin")
@Data
public class Coin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String unit;

    @Column(length = 64)
    private String name;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enable = true;

    /** 充币地址（平台统一地址，可选） */
    @Column(length = 128)
    private String depositAddress;
}
