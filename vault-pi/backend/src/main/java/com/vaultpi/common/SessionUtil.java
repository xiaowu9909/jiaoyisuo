package com.vaultpi.common;

import com.vaultpi.config.RequireLoginInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 统一从请求或会话中获取当前登录会员 ID。
 * 经 @RequireLogin 的接口优先从 RequestAttribute 读取（由 RequireLoginInterceptor 注入）。
 */
public final class SessionUtil {

    private static final String SESSION_MEMBER_ID = "memberId";

    private SessionUtil() {}

    /**
     * 从请求属性获取会员 ID（拦截器已注入），适用于需登录的接口。
     */
    public static Long getMemberId(HttpServletRequest request) {
        Object v = request.getAttribute(RequireLoginInterceptor.REQUEST_MEMBER_ID);
        return v instanceof Long ? (Long) v : null;
    }

    /**
     * 从 Session 获取会员 ID，用于仅有 HttpSession 的场景（如非 @RequireLogin 的校验逻辑）。
     */
    public static Long getMemberId(HttpSession session) {
        if (session == null) return null;
        Object v = session.getAttribute(SESSION_MEMBER_ID);
        return v instanceof Long ? (Long) v : null;
    }
}
