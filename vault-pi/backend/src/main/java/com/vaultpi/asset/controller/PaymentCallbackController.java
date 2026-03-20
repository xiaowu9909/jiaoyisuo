package com.vaultpi.asset.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付/充值回调 Stub：供第三方支付网关在支付成功后调用。
 * 当前仅记录日志并返回成功，对接真实网关时需：校验签名、幂等、创建/更新充值记录并调用入账逻辑。
 */
@Slf4j
@RestController
@RequestMapping(ApiPaths.BASE + "/asset/recharge")
public class PaymentCallbackController {

    /**
     * 充值回调 Stub。真实对接时：验证 sign、解析 orderNo/amount/status，创建 MemberDeposit 或调用入账。
     */
    @PostMapping("/callback")
    public Result<String> rechargeCallback(@RequestBody Map<String, Object> body) {
        log.info("Recharge callback received (stub): {}", body != null ? body.keySet() : "null");
        // TODO: 校验网关签名；幂等（同一 outTradeNo 不重复入账）；创建/更新 MemberDeposit 并触发入账
        return Result.ok("received");
    }
}
