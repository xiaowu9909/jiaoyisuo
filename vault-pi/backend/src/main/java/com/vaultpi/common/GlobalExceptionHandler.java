package com.vaultpi.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final boolean exposeErrorMessage;
    private final MessageSource messageSource;

    public GlobalExceptionHandler(Environment env,
                                  @Autowired(required = false) MessageSource messageSource) {
        this.exposeErrorMessage = env.getProperty("vaultpi.security.expose-error-message", Boolean.class, false)
            || java.util.Arrays.stream(env.getActiveProfiles()).anyMatch(p -> "dev".equalsIgnoreCase(p));
        this.messageSource = messageSource;
    }

    private String resolveMessage(String code, String defaultMessage) {
        if (messageSource == null) return defaultMessage;
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            return defaultMessage;
        }
    }

    /** 接口路径未找到（如未重新编译/重启导致管理端接口未注册）。对 /api 请求返回明确提示 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<String> handleNoResourceFound(NoResourceFoundException e) {
        String path = e.getResourcePath() != null ? e.getResourcePath() : "";
        log.warn("No handler for path: {} (method: {})", path, e.getHttpMethod());
        if (path.startsWith("api/") || path.startsWith("/api")) {
            return Result.fail(ErrorCode.API_NOT_FOUND, resolveMessage("error.api_not_found", "API not found."));
        }
        return Result.fail(ErrorCode.NOT_FOUND, resolveMessage("error.not_found", "Resource not found."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse(resolveMessage("error.validation", "Validation failed"));
        log.warn("Validation failed: {}", msg);
        return Result.fail(ErrorCode.VALIDATION_FAILED, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Business exception: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<String> handleIllegalStateException(IllegalStateException e) {
        log.warn("State exception: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    /** 业务异常：返回友好错误，不记录堆栈 */
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        int code = e.getCode();
        return code == ErrorCode.VALIDATION_FAILED.getCode()
            ? Result.fail(ErrorCode.VALIDATION_FAILED, e.getMessage())
            : Result.fail(code, e.getMessage());
    }

    /** 系统异常：记录完整堆栈并告警，对客户端仅返回通用文案 */
    @ExceptionHandler(SystemException.class)
    public Result<String> handleSystemException(SystemException e) {
        log.error("System exception: ", e);
        String msg = exposeErrorMessage && e.getMessage() != null && !e.getMessage().isBlank()
            ? e.getMessage() : resolveMessage("error.server_error", "Internal server error");
        return Result.fail(ErrorCode.SERVER_ERROR, msg);
    }

    /** 数据库/Redis 等数据访问异常：记录完整堆栈，对客户端仅返回统一文案，避免泄露内部信息 */
    @ExceptionHandler(DataAccessException.class)
    public Result<String> handleDataAccessException(DataAccessException e) {
        log.error("Data access exception: ", e);
        String msg = exposeErrorMessage && e.getMessage() != null && !e.getMessage().isBlank()
            ? e.getMessage() : resolveMessage("error.data_access", "Data service temporarily unavailable.");
        return Result.fail(ErrorCode.SERVER_ERROR, msg);
    }

    /** 兜底：未分类异常统一记录并返回 500；业务/系统异常已分层处理，此处多为 OOM、连接中断等需关注 */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("Unhandled exception: ", e);
        String msg = exposeErrorMessage && e.getMessage() != null && !e.getMessage().isBlank()
            ? e.getMessage() : resolveMessage("error.server_error", "Internal server error");
        return Result.fail(ErrorCode.SERVER_ERROR, msg);
    }
}
