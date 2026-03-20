package com.vaultpi.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 将 B 端维护的简体中文内容自动翻译为英文（C 端英文界面使用）。
 * 使用 MyMemory 免费 API：https://mymemory.translated.net/doc/spec.php
 */
@Service
public class TranslationService {

    private static final String MYMEMORY_URL = "https://api.mymemory.translated.net/get?langpair=zh-CN|en&q=";
    private static final int MAX_TEXT_LENGTH = 450; // API 单次请求建议不超过约 500 字符
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将中文文本翻译为英文。失败或空输入时返回原文。
     */
    public String translateToEnglish(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            if (encoded.length() > 2000) return text; // URL 长度限制，过长不翻译
            URI uri = URI.create(MYMEMORY_URL + encoded);
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri).GET().build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return text;
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode translated = root.path("responseData").path("translatedText");
            if (translated.isMissingNode() || !translated.isTextual()) return text;
            String result = translated.asText().trim();
            return result.isEmpty() ? text : result;
        } catch (Exception e) {
            return text;
        }
    }

    private static final List<String> SKIP_KEYS = List.of("imageUrl", "headerLogoUrl", "footerLogoUrl");

    /**
     * 递归将 Map 中的中文字符串值翻译为英文（跳过 URL 等字段）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> translateMapToEnglish(Map<String, Object> map) {
        if (map == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (SKIP_KEYS.contains(key) || value == null) {
                out.put(key, value);
                continue;
            }
            if (value instanceof String) {
                String s = (String) value;
                out.put(key, (s.isBlank() || isLikelyUrl(s)) ? s : translateToEnglish(s));
            } else if (value instanceof Map) {
                out.put(key, translateMapToEnglish((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String) {
                        String s = (String) item;
                        newList.add(s.isBlank() || isLikelyUrl(s) ? s : translateToEnglish(s));
                    } else if (item instanceof Map) {
                        newList.add(translateMapToEnglish((Map<String, Object>) item));
                    } else {
                        newList.add(item);
                    }
                }
                out.put(key, newList);
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    private static boolean isLikelyUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("/");
    }
}
