package com.vaultpi.config;

import com.vaultpi.exchange.orderbook.MatchBatchPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 优雅关闭：容器停止前将未落库的成交批次刷入数据库，避免停机丢成交导致资金与订单不一致。
 * 最多等待 10 秒；超时后仍退出，由对账流程兜底。
 */
@Order(0)
@Component
@ConditionalOnBean(MatchBatchPersistence.class)
public class GracefulShutdownFlush implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownFlush.class);
    private static final int SHUTDOWN_FLUSH_TIMEOUT_SECONDS = 10;

    private final MatchBatchPersistence batchPersistence;

    public GracefulShutdownFlush(MatchBatchPersistence batchPersistence) {
        this.batchPersistence = batchPersistence;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        int pending = batchPersistence.getPendingCount();
        if (pending == 0) {
            log.info("Graceful shutdown: no pending batch, skip flush");
            return;
        }
        log.info("Graceful shutdown: flushing pending batch (size={}), timeout={}s", pending, SHUTDOWN_FLUSH_TIMEOUT_SECONDS);
        long deadline = System.currentTimeMillis() + SHUTDOWN_FLUSH_TIMEOUT_SECONDS * 1000L;
        int flushed = 0;
        while (batchPersistence.getPendingCount() > 0 && System.currentTimeMillis() < deadline) {
            try {
                batchPersistence.flush();
                flushed++;
            } catch (Exception e) {
                log.error("Shutdown flush attempt failed", e);
                break;
            }
        }
        int remaining = batchPersistence.getPendingCount();
        if (remaining > 0) {
            log.warn("Graceful shutdown: {} items still pending after timeout; require manual reconciliation", remaining);
        } else {
            log.info("Graceful shutdown: batch flush completed, flushed {} cycle(s)", flushed);
        }
    }
}
