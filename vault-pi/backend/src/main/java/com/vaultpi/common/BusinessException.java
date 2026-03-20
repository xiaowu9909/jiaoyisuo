package com.vaultpi.common;

/**
 * 业务异常：可向用户展示的友好错误（如参数不合法、状态不允许）。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.PARAM_INVALID.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode != null ? errorCode.getCode() : ErrorCode.PARAM_INVALID.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
