package com.vaultpi.market.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 记录“热门实盘 TopN 交易对”快照，避免服务重启后重新从 Kraken 拉取造成列表波动。
 */
@Entity
@Table(name = "top_real_coins_snapshot")
@Data
public class TopRealCoinsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer count;

    /**
     * symbols 按顺序存储（JSON 数组字符串），元素形如 "ETH/USDT"
     */
    @Lob
    @Column(name = "symbols_json", nullable = false)
    private String symbolsJson;

    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;
}

