package com.vaultpi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 可选 CSRF 校验：当 vaultpi.security.csrf-enabled=true 时，
 * 对 /api 下除登录、注册等白名单外的 POST/PUT/DELETE/PATCH 要求请求头 X-CSRF-TOKEN 与 session 中 csrfToken 一致。
 */
public class CsrfFilter extends OncePerRequestFilter {

    private static final String SESSION_CSRF_TOKEN = "csrfToken";
    private static final String HEADER_CSRF_TOKEN = "X-CSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> CSRF_EXEMPT_PATHS = Set.of(
        "/api/login", "/api/register", "/api/check/login",
        "/api/login/password", "/api/register/send-code", "/api/register/send-email-code",
        "/api/admin/authenticate"
    );

    private final boolean csrfEnabled;

    public CsrfFilter(boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!csrfEnabled || SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (path == null) path = "";
        if (!path.startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean exempt = CSRF_EXEMPT_PATHS.stream().anyMatch(path::equals);
        if (exempt) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        String expected = session != null ? (String) session.getAttribute(SESSION_CSRF_TOKEN) : null;
        String provided = request.getHeader(HEADER_CSRF_TOKEN);
        // multipart/form-data 等场景可能无法通过请求头传 token，支持从表单字段读取
        if ((provided == null || provided.isEmpty()) && isMultipartContent(request)) {
            String fromParam = request.getParameter("_csrf");
            if (fromParam == null || fromParam.isEmpty()) fromParam = request.getParameter("csrfToken");
            if (fromParam != null && !fromParam.isEmpty()) provided = fromParam;
        }
        if (expected == null || expected.isEmpty() || !expected.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"缺少或无效的 CSRF 令牌\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isMultipartContent(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
}
