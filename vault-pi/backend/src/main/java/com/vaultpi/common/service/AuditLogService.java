package com.vaultpi.common.service;

import com.vaultpi.common.entity.AuditLog;
import com.vaultpi.common.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    public void log(Long userId, String action, String detail, String ip) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setAction(action);
            log.setDetail(detail != null && detail.length() > 512 ? detail.substring(0, 512) : detail);
            log.setIp(ip);
            repository.save(log);
        } catch (Exception ignored) {
            // 审计写失败不阻塞主流程
        }
    }

    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    public void logLoginSuccess(Long userId, String username, jakarta.servlet.http.HttpServletRequest request) {
        log(userId, AuditLog.ACTION_LOGIN_SUCCESS, username, clientIp(request));
    }

    public void logLoginFail(String username, jakarta.servlet.http.HttpServletRequest request) {
        log(null, AuditLog.ACTION_LOGIN_FAIL, username, clientIp(request));
    }

    public void logPasswordUpdate(Long userId, jakarta.servlet.http.HttpServletRequest request) {
        log(userId, AuditLog.ACTION_PASSWORD_UPDATE, null, clientIp(request));
    }

    public void logPasswordReset(Long userId, jakarta.servlet.http.HttpServletRequest request) {
        log(userId, AuditLog.ACTION_PASSWORD_RESET, null, clientIp(request));
    }
}
