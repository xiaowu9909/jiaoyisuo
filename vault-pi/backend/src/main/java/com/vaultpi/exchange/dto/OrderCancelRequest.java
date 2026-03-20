package com.vaultpi.exchange.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "撤单请求")
public class OrderCancelRequest {

    @NotBlank(message = "缺少 orderId")
    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderId;
}
