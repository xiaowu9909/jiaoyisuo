package com.vaultpi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * 最先执行的 CORS 过滤器，确保预检(OPTIONS)和跨域请求都带上 CORS 头。
 * 开发环境允许 localhost:5173 / 5174 与 127.0.0.1:5173 / 5174。
 */
public class SimpleCorsFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(Arrays.asList(
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5174"
    ));

    private static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS, HEAD";
    /** 使用 credentials 时不能为 *，需显式列出（含 Content-Type 等） */
    private static final String ALLOWED_HEADERS = "Content-Type, Authorization, Accept, Origin, X-Requested-With";
    private static final String EXPOSED_HEADERS = "x-auth-token";
    private static final long MAX_AGE = 3600;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
            response.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            response.setHeader("Access-Control-Expose-Headers", EXPOSED_HEADERS);
            response.setHeader("Access-Control-Max-Age", String.valueOf(MAX_AGE));
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
