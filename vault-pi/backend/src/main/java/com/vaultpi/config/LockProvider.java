package com.vaultpi.config;

import java.util.function.Supplier;

/**
 * 分布式锁抽象，支持 Redisson 实现与无 Redis 时的空实现。
 */
public interface LockProvider {

    <T> T runWithLock(String lockKey, Supplier<T> supplier);
}
