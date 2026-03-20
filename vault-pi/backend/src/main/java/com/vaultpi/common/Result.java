package com.vaultpi.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "SUCCESS", data);
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(0, message != null ? message : "SUCCESS", data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 使用错误码，消息可覆盖（如校验详情） */
    public static <T> Result<T> fail(ErrorCode errorCode, String messageOverride) {
        return new Result<>(errorCode.getCode(), messageOverride != null && !messageOverride.isBlank() ? messageOverride : errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }
}
