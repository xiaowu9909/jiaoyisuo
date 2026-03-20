package com.vaultpi.common;

/**
 * API 路径常量，支持版本前缀，为未来 v2 预留。
 * 当前同时暴露 /api 与 /api/v1，客户端可逐步迁移到 /api/v1。
 */
public final class ApiPaths {

    /** 无版本前缀（兼容旧客户端） */
    public static final String BASE = "/api";
    /** 版本 v1 前缀，新客户端建议使用 */
    public static final String V1 = "/api/v1";

    private ApiPaths() {}
}
