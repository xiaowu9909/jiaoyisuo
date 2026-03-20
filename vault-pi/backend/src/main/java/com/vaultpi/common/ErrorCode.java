package com.vaultpi.common;

import lombok.Getter;

/**
 * 统一错误码：客户端可根据 code 做分支处理。
 * 1xxx 参数/校验，2xxx 业务（余额、订单等），3xxx 认证/权限，5xxx 服务端。
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "SUCCESS"),

    PARAM_INVALID(1001, "参数错误"),
    PARAM_MISSING(1002, "参数不完整"),
    VALIDATION_FAILED(1003, "参数校验失败"),

    BALANCE_INSUFFICIENT(2001, "余额不足"),
    ORDER_NOT_FOUND(2002, "订单不存在"),
    ORDER_STATE_INVALID(2003, "订单状态不可操作"),
    SYMBOL_NOT_FOUND(2004, "交易对不存在"),
    WALLET_NOT_FOUND(2005, "钱包不存在"),

    UNAUTHORIZED(3001, "请先登录"),
    FORBIDDEN(3002, "无权操作"),
    NEED_REALNAME(3003, "请先完成实名认证"),
    NEED_WITHDRAW_PWD(3004, "请先设置提现密码"),

    NOT_FOUND(4001, "资源不存在"),
    API_NOT_FOUND(4002, "接口不存在"),

    SERVER_ERROR(5001, "服务器内部错误"),
    RATE_LIMIT(5002, "操作过于频繁，请 60 秒后重试"),
    IDEMPOTENCY_CONFLICT(5003, "重复的请求，请使用幂等键"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
