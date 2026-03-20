package com.vaultpi.config;

import com.vaultpi.common.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class RequireLoginInterceptor implements HandlerInterceptor {

    public static final String SESSION_MEMBER_ID = "memberId";
    public static final String REQUEST_MEMBER_ID = "memberId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) return true;
        HandlerMethod hm = (HandlerMethod) handler;
        boolean required = hm.getMethodAnnotation(RequireLogin.class) != null
            || hm.getBeanType().isAnnotationPresent(RequireLogin.class);
        if (!required) return true;

        HttpSession session = request.getSession(false);
        Long memberId = session != null ? (Long) session.getAttribute(SESSION_MEMBER_ID) : null;
        if (memberId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;
        }
        request.setAttribute(REQUEST_MEMBER_ID, memberId);
        return true;
    }
}
