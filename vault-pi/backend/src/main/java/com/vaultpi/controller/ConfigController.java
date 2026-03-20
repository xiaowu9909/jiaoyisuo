package com.vaultpi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.config.TranslationService;
import com.vaultpi.system.repository.SystemConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开配置接口（C 端首页等使用，无需登录）
 */
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/config", ApiPaths.V1 + "/config" })
public class ConfigController {

    private static final String KEY_HOME_GETTING_START = "home_getting_start";
    private static final String KEY_HOME_ABOUT_BRAND = "home_about_brand";
    private static final String KEY_HOME_APP_DOWNLOAD = "home_app_download";
    private static final String KEY_SITE_LOGO = "site_logo";

    private final SystemConfigRepository configRepository;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConfigController(SystemConfigRepository configRepository, TranslationService translationService) {
        this.configRepository = configRepository;
        this.translationService = translationService;
    }

    /**
     * 首页「新手入门」区块配置：标题、副标题、四个卡片（名称 + 描述）
     * 返回格式：{ title, subtitle, items: [ { name, tips }, ... ] }
     */
    @GetMapping("/home-getting-start")
    public ResponseEntity<Result<Map<String, Object>>> homeGettingStart(
            @RequestParam(required = false, defaultValue = "") String lang) {
        Result<Map<String, Object>> body = configRepository.findById(KEY_HOME_GETTING_START)
                .map(config -> {
                    String value = config.getValue();
                    if (value == null || value.isBlank()) return Result.<Map<String, Object>>ok(null);
                    try {
                        Map<String, Object> data = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
                        if ("en".equalsIgnoreCase(lang) && data != null) {
                            data = translationService.translateMapToEnglish(data);
                        }
                        return Result.ok(data);
                    } catch (Exception e) {
                        return Result.<Map<String, Object>>ok(null);
                    }
                })
                .orElse(Result.<Map<String, Object>>ok(null));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }

    /**
     * 首页「关于 Vault π」区块配置：标题、副标题（如 诚实|公平|热情|开放）、两段描述
     * 返回格式：{ title, detail, desc1, desc2 }
     */
    @GetMapping("/home-about-brand")
    public ResponseEntity<Result<Map<String, Object>>> homeAboutBrand(
            @RequestParam(required = false, defaultValue = "") String lang) {
        Result<Map<String, Object>> body = configRepository.findById(KEY_HOME_ABOUT_BRAND)
                .map(config -> {
                    String value = config.getValue();
                    if (value == null || value.isBlank()) return Result.<Map<String, Object>>ok(null);
                    try {
                        Map<String, Object> data = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
                        if ("en".equalsIgnoreCase(lang) && data != null) {
                            data = translationService.translateMapToEnglish(data);
                        }
                        return Result.ok(data);
                    } catch (Exception e) {
                        return Result.<Map<String, Object>>ok(null);
                    }
                })
                .orElse(Result.<Map<String, Object>>ok(null));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }

    /**
     * 首页「扫描二维码，下载APP」区块配置
     * 返回格式：{ scanText }，可选 downloadText、slogan
     */
    @GetMapping("/home-app-download")
    public ResponseEntity<Result<Map<String, Object>>> homeAppDownload(
            @RequestParam(required = false, defaultValue = "") String lang) {
        Result<Map<String, Object>> body = configRepository.findById(KEY_HOME_APP_DOWNLOAD)
                .map(config -> {
                    String value = config.getValue();
                    if (value == null || value.isBlank()) return Result.<Map<String, Object>>ok(null);
                    try {
                        Map<String, Object> data = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
                        if ("en".equalsIgnoreCase(lang) && data != null) {
                            data = translationService.translateMapToEnglish(data);
                        }
                        return Result.ok(data);
                    } catch (Exception e) {
                        return Result.<Map<String, Object>>ok(null);
                    }
                })
                .orElse(Result.<Map<String, Object>>ok(null));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }

    /**
     * C 端全局 Logo 配置：头部 Logo、底部 Logo 的图片地址（留空则使用默认）
     * 返回格式：{ headerLogoUrl, footerLogoUrl }
     */
    @GetMapping("/site-logo")
    public ResponseEntity<Result<Map<String, Object>>> siteLogo() {
        Result<Map<String, Object>> body = configRepository.findById(KEY_SITE_LOGO)
                .map(config -> {
                    String value = config.getValue();
                    if (value == null || value.isBlank()) return Result.<Map<String, Object>>ok(null);
                    try {
                        Map<String, Object> data = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
                        return Result.ok(data);
                    } catch (Exception e) {
                        return Result.<Map<String, Object>>ok(null);
                    }
                })
                .orElse(Result.<Map<String, Object>>ok(null));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }
}
