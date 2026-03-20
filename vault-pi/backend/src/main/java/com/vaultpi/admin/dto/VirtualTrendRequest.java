package com.vaultpi.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设置虚拟盘趋势请求：单次涨跌幅与周期有边界限制，防止极端参数导致价格瞬时跳变。
 */
@Data
public class VirtualTrendRequest {

    @NotBlank(message = "交易对不能为空")
    private String symbol;

    /** UP 或 DOWN */
    @NotBlank(message = "方向须为 UP 或 DOWN")
    private String direction;

    /** 涨跌幅（0, 10]，单次最大 10% 防止瞬时跳变 */
    @NotNull(message = "涨跌幅不能为空")
    @DecimalMin(value = "0.01", message = "涨跌幅须大于 0")
    @DecimalMax(value = "10", message = "单次涨跌幅不得超过 10%")
    private BigDecimal percent;

    /** 周期（秒），至少 300（5 分钟） */
    @NotNull(message = "周期不能为空")
    @Min(value = 300, message = "周期须不少于 5 分钟（300 秒）")
    private Integer duration;
}
