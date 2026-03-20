package com.vaultpi.config;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 事务感知分布式锁：在存在事务时，将解锁推迟到事务提交或回滚之后，避免「先释放锁再提交」导致的并发资金操作。
 * 无事务时行为与普通分布式锁一致（执行完即释放）。
 */
@Primary
@Component
@ConditionalOnBean(RedissonClient.class)
public class TransactionAwareDistributedLockProvider implements LockProvider {

    private static final long WAIT_SECONDS = 5;
    private static final long LEASE_SECONDS = 60;

    private final RedissonClient redissonClient;

    public TransactionAwareDistributedLockProvider(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T runWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        boolean deferUnlock = false;
        try {
            acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            T result = supplier.get();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                deferUnlock = true;
                final RLock lockRef = lock;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (lockRef.isHeldByCurrentThread()) {
                            lockRef.unlock();
                        }
                    }
                });
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("操作被中断");
        } finally {
            if (acquired && !deferUnlock && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
