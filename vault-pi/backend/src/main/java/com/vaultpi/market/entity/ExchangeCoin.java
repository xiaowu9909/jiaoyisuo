package com.vaultpi.market.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "exchange_coin")
@Data
public class ExchangeCoin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String symbol;

    @Column(name = "base_symbol", nullable = false, length = 16)
    private String baseSymbol;

    @Column(name = "coin_symbol", nullable = false, length = 16)
    private String coinSymbol;

    /** 基币精度（价格小数位） */
    @Column(name = "base_coin_precision")
    private Integer baseCoinPrecision;

    /** 交易币精度（数量小数位） */
    @Column(name = "coin_precision")
    private Integer coinPrecision;

    @Column(nullable = false)
    private Boolean enable = true;

    /** 是否虚拟盘（自设价格，不接交易所行情） */
    @Column(name = "is_virtual")
    private Boolean virtual = false;

    /** 虚拟盘自设价格（仅当 virtual=true 时有效，兼容旧数据） */
    @Column(name = "custom_price", precision = 24, scale = 8)
    private BigDecimal customPrice;

    /** 虚拟盘价格区间下限（与 customPriceHigh 同时存在时优先使用区间） */
    @Column(name = "custom_price_low", precision = 24, scale = 8)
    private BigDecimal customPriceLow;

    /** 虚拟盘价格区间上限 */
    @Column(name = "custom_price_high", precision = 24, scale = 8)
    private BigDecimal customPriceHigh;

    /** 虚拟盘行情活跃度：NORMAL=一般, ACTIVE=活跃, HOT=热门（影响波动与深度量） */
    @Column(name = "virtual_activity", length = 16)
    private String virtualActivity = "NORMAL";

    /** 虚拟盘趋势方向：UP=上涨, DOWN=下跌（与 trendPercent/duration/start 一起使用） */
    @Column(name = "trend_direction", length = 8)
    private String trendDirection;

    /** 虚拟盘趋势涨跌幅（如 5 表示 5%） */
    @Column(name = "trend_percent", precision = 10, scale = 4)
    private BigDecimal trendPercent;

    /** 虚拟盘趋势周期（秒），如 1800 表示 30 分钟内达到目标 */
    @Column(name = "trend_duration")
    private Integer trendDuration;

    /** 虚拟盘趋势开始时间（毫秒时间戳） */
    @Column(name = "trend_start_time")
    private Long trendStartTime;

    /** 虚拟盘趋势起始价格（开始时的中心价，用于按比例插值） */
    @Column(name = "trend_start_price", precision = 24, scale = 8)
    private BigDecimal trendStartPrice;

    /** GBM 日趋势因子（如 0.01 表示每日约涨 1%，-0.005 表示每日约跌 0.5%） */
    @Column(name = "virtual_drift_daily", precision = 10, scale = 6)
    private BigDecimal virtualDriftDaily;

    /** GBM 波动率（如 0.02 表示约 2% 波动，影响 K 线影线/实体） */
    @Column(name = "virtual_volatility", precision = 10, scale = 6)
    private BigDecimal virtualVolatility;

    /** 虚拟盘深度 Tick 间距（如 0.1），中心辐射挂单用 */
    @Column(name = "virtual_tick_size", precision = 18, scale = 8)
    private BigDecimal virtualTickSize;

    /** 单笔最小数量（下单前校验） */
    @Column(name = "min_amount", precision = 26, scale = 16)
    private BigDecimal minAmount;

    /** 单笔最大数量（下单前校验） */
    @Column(name = "max_amount", precision = 26, scale = 16)
    private BigDecimal maxAmount;

    /** 单笔最小名义价值（基币，如 USDT；下单前校验 price*amount >= minNotional） */
    @Column(name = "min_notional", precision = 26, scale = 16)
    private BigDecimal minNotional;
}
