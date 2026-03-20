package com.vaultpi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * XSS 防护：将请求包装为 XssRequestWrapper，对 query 与 form 参数做 HTML 转义。
 * 仅对 /api 生效；JSON body 不在此处处理，前端渲染时须做输出转义。
 */
public class XssFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new XssRequestWrapper(request), response);
    }
}
