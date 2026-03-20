package com.vaultpi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 添加安全响应头，降低点击劫持、MIME 嗅探、XSS、数据注入等风险。
 * 含 CSP、HSTS（可配置）、Permissions-Policy。
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final String contentSecurityPolicy;
    private final long hstsMaxAgeSeconds;
    private final boolean hstsEnabled;

    public SecurityHeadersFilter(String contentSecurityPolicy, long hstsMaxAgeSeconds, boolean hstsEnabled) {
        this.contentSecurityPolicy = contentSecurityPolicy != null ? contentSecurityPolicy : "default-src 'self'";
        this.hstsMaxAgeSeconds = hstsMaxAgeSeconds > 0 ? hstsMaxAgeSeconds : 0;
        this.hstsEnabled = hstsEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        if (contentSecurityPolicy != null && !contentSecurityPolicy.isBlank()) {
            response.setHeader("Content-Security-Policy", contentSecurityPolicy);
        }
        if (hstsEnabled && hstsMaxAgeSeconds > 0) {
            response.setHeader("Strict-Transport-Security", "max-age=" + hstsMaxAgeSeconds + "; includeSubDomains; preload");
        }
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=(), usb=()");
        filterChain.doFilter(request, response);
    }
}
