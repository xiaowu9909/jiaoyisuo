package com.vaultpi.futures.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "期货下单请求")
public class FuturesOrderAddRequest {

    @NotBlank(message = "交易对不能为空")
    @Schema(description = "交易对，如 BTC/USDT", example = "BTC/USDT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @NotBlank(message = "方向不能为空")
    @Schema(description = "方向", allowableValues = { "LONG", "SHORT" }, requiredMode = Schema.RequiredMode.REQUIRED)
    private String direction;

    @NotBlank(message = "类型不能为空")
    @Schema(description = "订单类型", allowableValues = { "LIMIT", "MARKET" }, requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.00000001", message = "数量必须大于 0")
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "限价单价格；市价单也需传参考价用于计算保证金")
    private BigDecimal price;

    @Schema(description = "杠杆倍数，不传则使用会员最大可用杠杆")
    private Integer leverage;

    /** 业务校验：方向、类型、限价/市价价格 */
    public String validateBusiness() {
        if (!"LONG".equals(direction) && !"SHORT".equals(direction)) {
            return "方向必须为 LONG 或 SHORT";
        }
        if (!"LIMIT".equals(type) && !"MARKET".equals(type)) {
            return "类型必须为 LIMIT 或 MARKET";
        }
        if ("LIMIT".equals(type) && (price == null || price.compareTo(BigDecimal.ZERO) <= 0)) {
            return "限价单必须填写价格";
        }
        if ("MARKET".equals(type) && (price == null || price.compareTo(BigDecimal.ZERO) <= 0)) {
            return "市价单计算保证金需要参考价格";
        }
        return null;
    }
}
