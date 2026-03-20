package com.vaultpi.exchange.dto;

import com.vaultpi.market.entity.ExchangeCoin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "现货下单请求")
public class OrderAddRequest {

    private static final int DEFAULT_SCALE = 8;

    @NotBlank(message = "交易对不能为空")
    @Schema(description = "交易对符号", example = "BTC/USDT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @NotBlank(message = "方向不能为空")
    @Schema(description = "方向", allowableValues = { "BUY", "SELL" }, requiredMode = Schema.RequiredMode.REQUIRED)
    private String direction;

    @NotBlank(message = "类型不能为空")
    @Schema(description = "订单类型", allowableValues = { "LIMIT", "MARKET" }, requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0")
    @NotNull(message = "数量不能为空")
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "限价单价格，限价单必填")
    private BigDecimal price;

    @Schema(description = "幂等键，防重复提交；也可通过请求头 X-Idempotency-Key 传递")
    private String idempotencyKey;

    /** 业务校验：方向、类型、限价单价格 */
    public String validateBusiness() {
        if (!"BUY".equals(direction) && !"SELL".equals(direction)) {
            return "方向必须为 BUY 或 SELL";
        }
        if (!"LIMIT".equals(type) && !"MARKET".equals(type)) {
            return "类型必须为 LIMIT 或 MARKET";
        }
        if ("LIMIT".equals(type) && (price == null || price.compareTo(BigDecimal.ZERO) <= 0)) {
            return "限价单必须填写价格且大于 0";
        }
        return null;
    }

    /** 价格/数量小数位数校验，需传入交易对配置；超精度可能导致计算或存储异常 */
    public String validatePriceAndAmountScale(ExchangeCoin coin) {
        if (coin == null) return null;
        int priceScale = coin.getBaseCoinPrecision() != null ? coin.getBaseCoinPrecision() : DEFAULT_SCALE;
        int amountScale = coin.getCoinPrecision() != null ? coin.getCoinPrecision() : DEFAULT_SCALE;
        if (price != null && price.scale() > priceScale) {
            return "价格精度不得超过 " + priceScale + " 位小数";
        }
        if (amount != null && amount.scale() > amountScale) {
            return "数量精度不得超过 " + amountScale + " 位小数";
        }
        return null;
    }
}
