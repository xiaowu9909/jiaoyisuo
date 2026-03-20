package com.vaultpi.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端，用于分布式锁。与 spring.data.redis 共用同一 Redis 实例。
 * 关闭时设置 vaultpi.distributed-lock.enabled=false（如 dev 无 Redis 时）。
 */
@Configuration
@ConditionalOnProperty(name = "vaultpi.distributed-lock.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;
    @Value("${spring.data.redis.port:6379}")
    private int port;
    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String url = (password == null || password.isEmpty())
            ? "redis://" + host + ":" + port
            : "redis://:" + password + "@" + host + ":" + port;
        config.useSingleServer().setAddress(url);
        return Redisson.create(config);
    }
}
