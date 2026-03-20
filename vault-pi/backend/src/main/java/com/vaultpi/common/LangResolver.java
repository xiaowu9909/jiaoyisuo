package com.vaultpi.common;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * 从请求中解析语言：优先 query/param lang，其次 Accept-Language 头部，默认 CN。
 * 用于公告、帮助等按语言返回内容的 API。
 */
public final class LangResolver {

    private static final String DEFAULT_LANG = "CN";

    /** 支持的语言与 Accept-Language 的映射：zh* -> CN, en* -> en, 其他 -> CN */
    public static String resolve(HttpServletRequest request, String paramLang) {
        if (paramLang != null && !paramLang.isBlank()) {
            return normalize(paramLang);
        }
        String accept = request.getHeader("Accept-Language");
        if (accept == null || accept.isBlank()) {
            return DEFAULT_LANG;
        }
        // 取第一个偏好，如 "en-US,en;q=0.9,zh-CN;q=0.8" -> "en-US"
        String first = accept.split(",")[0].trim().split(";")[0].trim();
        if (first.toLowerCase().startsWith("zh")) return "CN";
        if (first.toLowerCase().startsWith("en")) return "en";
        return DEFAULT_LANG;
    }

    private static String normalize(String lang) {
        String s = lang.trim().toUpperCase();
        if ("EN".equals(s)) return "en";
        if ("CN".equals(s) || "ZH".equals(s)) return "CN";
        return s;
    }
}
