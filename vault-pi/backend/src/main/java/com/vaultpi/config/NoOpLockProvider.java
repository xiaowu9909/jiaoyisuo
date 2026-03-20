package com.vaultpi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/**
 * 当 Redisson 未启用时（如 dev 无 Redis），提供无锁实现，直接执行逻辑。
 */
@Configuration
public class NoOpLockProvider {

    @Bean
    @ConditionalOnMissingBean(LockProvider.class)
    public LockProvider noOpLockProvider() {
        return new LockProvider() {
            @Override
            public <T> T runWithLock(String lockKey, Supplier<T> supplier) {
                return supplier.get();
            }
        };
    }
}
