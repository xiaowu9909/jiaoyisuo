package com.vaultpi.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对请求参数值进行 HTML 转义，降低反射型 XSS 风险。
 * 仅包装 query 与 form 参数；JSON body 由业务层校验长度/格式，前端渲染时须转义。
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String[]> escapedParameterMap;

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
        Map<String, String[]> raw = request.getParameterMap();
        if (raw == null || raw.isEmpty()) {
            escapedParameterMap = Collections.emptyMap();
        } else {
            escapedParameterMap = new LinkedHashMap<>(raw.size());
            for (Map.Entry<String, String[]> e : raw.entrySet()) {
                String key = e.getKey();
                String[] vals = e.getValue();
                if (vals != null) {
                    String[] escaped = new String[vals.length];
                    for (int i = 0; i < vals.length; i++) {
                        escaped[i] = escapeHtml(vals[i]);
                    }
                    escapedParameterMap.put(key, escaped);
                } else {
                    escapedParameterMap.put(key, null);
                }
            }
        }
    }

    @Override
    public String getParameter(String name) {
        String[] vals = escapedParameterMap.get(name);
        return (vals != null && vals.length > 0) ? vals[0] : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        return escapedParameterMap.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(escapedParameterMap);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(escapedParameterMap.keySet());
    }

    /**
     * HTML 转义：&lt; &gt; &amp; &quot; &#x27; &#x2F; 等，防止注入脚本与属性。
     */
    public static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) return value;
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                case '/' -> sb.append("&#x2F;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
