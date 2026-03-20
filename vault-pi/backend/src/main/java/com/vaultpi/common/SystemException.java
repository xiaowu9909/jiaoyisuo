package com.vaultpi.common;

/**
 * 系统异常：需记录日志并告警，对客户端仅返回通用错误文案，避免泄露内部信息。
 */
public class SystemException extends RuntimeException {

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemException(Throwable cause) {
        super(cause != null ? cause.getMessage() : "系统错误", cause);
    }
}
