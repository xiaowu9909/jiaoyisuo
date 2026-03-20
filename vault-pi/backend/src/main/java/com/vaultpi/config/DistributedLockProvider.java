package com.vaultpi.config;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的分布式锁（无事务感知）。生产环境优先使用 {@link TransactionAwareDistributedLockProvider}；
 * 本类保留供非事务场景或测试使用，默认不注册为 Bean（由 TransactionAwareDistributedLockProvider 作为 LockProvider）。
 */
@ConditionalOnBean(RedissonClient.class)
public class DistributedLockProvider implements LockProvider {

    private static final long WAIT_SECONDS = 5;
    /** 租期需大于单次下单+撮合+持久化最坏耗时，避免锁提前释放导致并发资金操作 */
    private static final long LEASE_SECONDS = 60;

    private final RedissonClient redissonClient;

    public DistributedLockProvider(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T runWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
