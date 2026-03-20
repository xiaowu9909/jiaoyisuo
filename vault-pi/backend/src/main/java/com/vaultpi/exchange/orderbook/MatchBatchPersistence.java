package com.vaultpi.exchange.orderbook;

import com.vaultpi.exchange.event.OrderTradeEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;

/**
 * 成交后积累批次，定时 100ms 使用 JdbcTemplate.batchUpdate 批量写入数据库；
 * 钱包+订单+成交+流水在同一事务内提交，事务提交后统一发布成交通知事件。
 * 失败时回滚并重新入队，记录失败指标。
 */
@Component
public class MatchBatchPersistence {

    private static final Logger log = LoggerFactory.getLogger(MatchBatchPersistence.class);
    private static final String UPDATE_ORDER = "UPDATE exchange_order SET traded_amount = ?, status = ? WHERE order_id = ?";
    private static final String INSERT_TRADE = "INSERT INTO exchange_trade (symbol, direction, price, amount, create_time) VALUES (?, ?, ?, ?, ?)";
    private static final String INSERT_TX = "INSERT INTO member_transaction (member_id, amount, type, symbol, fee, create_time) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_WALLET = "UPDATE member_wallet SET balance = balance + ?, frozen_balance = frozen_balance + ?, version = version + 1 WHERE id = ? AND version = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    private final ConcurrentLinkedQueue<OrderUpdate> pendingOrderUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TradeInsert> pendingTrades = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TxInsert> pendingTransactions = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WalletUpdate> pendingWalletUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OrderTradeEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    public MatchBatchPersistence(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher,
                                  MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("vaultpi.batch.persistence.queue.size", this, MatchBatchPersistence::getPendingCount);
    }

    public void addOrderUpdate(String orderId, BigDecimal tradedAmount, String status) {
        pendingOrderUpdates.add(new OrderUpdate(orderId, tradedAmount, status));
    }

    public void addWalletUpdate(Long walletId, BigDecimal balanceDelta, BigDecimal frozenDelta, Long version) {
        pendingWalletUpdates.add(new WalletUpdate(walletId, balanceDelta, frozenDelta, version));
    }

    public void addTrade(String symbol, String direction, BigDecimal price, BigDecimal amount, Instant createTime) {
        pendingTrades.add(new TradeInsert(symbol, direction, price, amount, createTime != null ? createTime : Instant.now()));
    }

    public void addTransaction(Long memberId, BigDecimal amount, String type, String symbol, BigDecimal fee) {
        pendingTransactions.add(new TxInsert(memberId, amount, type, symbol, fee != null ? fee : BigDecimal.ZERO));
    }

    public void addEvent(OrderTradeEvent event) {
        pendingEvents.add(event);
    }

    /** 当前队列积压量（订单+成交+流水+钱包），供监控 */
    public int getPendingCount() {
        return pendingOrderUpdates.size() + pendingTrades.size() + pendingTransactions.size()
            + pendingWalletUpdates.size();
    }

    /**
     * 由定时任务调用（如每 100ms）：将当前批次刷入数据库并在提交后发布事件。
     * 钱包+订单+成交+流水同一事务；失败时回滚并重新入队，记录指标。
     */
    @Transactional
    public void flush() {
        List<OrderUpdate> orders = drain(pendingOrderUpdates);
        List<TradeInsert> trades = drain(pendingTrades);
        List<TxInsert> txs = drain(pendingTransactions);
        List<WalletUpdate> wallets = drain(pendingWalletUpdates);
        List<OrderTradeEvent> events = drain(pendingEvents);
        if (orders.isEmpty() && trades.isEmpty() && txs.isEmpty() && wallets.isEmpty()) {
            if (!events.isEmpty()) {
                for (OrderTradeEvent e : events) eventPublisher.publishEvent(e);
            }
            return;
        }
        try {
            if (!wallets.isEmpty()) {
                for (WalletUpdate w : wallets) {
                    int updated = jdbcTemplate.update(UPDATE_WALLET,
                        w.balanceDelta, w.frozenDelta, w.walletId, w.version);
                    if (updated != 1) {
                        throw new OptimisticLockException("钱包乐观锁冲突 walletId=" + w.walletId);
                    }
                }
            }
            if (!orders.isEmpty()) {
                jdbcTemplate.batchUpdate(UPDATE_ORDER, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        OrderUpdate u = orders.get(i);
                        ps.setBigDecimal(1, u.tradedAmount);
                        ps.setString(2, u.status);
                        ps.setString(3, u.orderId);
                    }
                    @Override
                    public int getBatchSize() { return orders.size(); }
                });
            }
            if (!trades.isEmpty()) {
                jdbcTemplate.batchUpdate(INSERT_TRADE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TradeInsert t = trades.get(i);
                        ps.setString(1, t.symbol);
                        ps.setString(2, t.direction);
                        ps.setBigDecimal(3, t.price);
                        ps.setBigDecimal(4, t.amount);
                        ps.setObject(5, trades.get(i).createTime);
                    }
                    @Override
                    public int getBatchSize() { return trades.size(); }
                });
            }
            if (!txs.isEmpty()) {
                Instant now = Instant.now();
                jdbcTemplate.batchUpdate(INSERT_TX, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TxInsert t = txs.get(i);
                        ps.setLong(1, t.memberId);
                        ps.setBigDecimal(2, t.amount);
                        ps.setString(3, t.type);
                        ps.setString(4, t.symbol);
                        ps.setBigDecimal(5, t.fee);
                        ps.setObject(6, now);
                    }
                    @Override
                    public int getBatchSize() { return txs.size(); }
                });
            }
            final List<OrderTradeEvent> toPublish = new ArrayList<>(events);
            TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (OrderTradeEvent e : toPublish) eventPublisher.publishEvent(e);
                    }
                });
        } catch (Exception e) {
            meterRegistry.counter("vaultpi.batch.persistence.failures").increment();
            log.error("批量持久化失败，批次大小: orders={}, trades={}, txs={}, wallets={}",
                orders.size(), trades.size(), txs.size(), wallets.size(), e);
            requeue(orders, trades, txs, wallets, events);
            throw e;
        }
    }

    private void requeue(List<OrderUpdate> orders, List<TradeInsert> trades, List<TxInsert> txs,
                         List<WalletUpdate> wallets, List<OrderTradeEvent> events) {
        for (OrderUpdate u : orders) pendingOrderUpdates.add(u);
        for (TradeInsert t : trades) pendingTrades.add(t);
        for (TxInsert t : txs) pendingTransactions.add(t);
        for (WalletUpdate w : wallets) pendingWalletUpdates.add(w);
        for (OrderTradeEvent e : events) pendingEvents.add(e);
    }

    /** 钱包乐观锁冲突或批量更新失败时抛出，触发事务回滚 */
    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }

    private static <T> List<T> drain(ConcurrentLinkedQueue<T> queue) {
        List<T> list = new ArrayList<>();
        T t;
        while ((t = queue.poll()) != null) list.add(t);
        return list;
    }

    public static final class OrderUpdate {
        final String orderId;
        final BigDecimal tradedAmount;
        final String status;

        OrderUpdate(String orderId, BigDecimal tradedAmount, String status) {
            this.orderId = orderId;
            this.tradedAmount = tradedAmount;
            this.status = status;
        }
    }

    public static final class TradeInsert {
        final String symbol;
        final String direction;
        final BigDecimal price;
        final BigDecimal amount;
        final Instant createTime;

        TradeInsert(String symbol, String direction, BigDecimal price, BigDecimal amount, Instant createTime) {
            this.symbol = symbol;
            this.direction = direction;
            this.price = price;
            this.amount = amount;
            this.createTime = createTime;
        }
    }

    public static final class TxInsert {
        final long memberId;
        final BigDecimal amount;
        final String type;
        final String symbol;
        final BigDecimal fee;

        TxInsert(Long memberId, BigDecimal amount, String type, String symbol, BigDecimal fee) {
            this.memberId = memberId;
            this.amount = amount;
            this.type = type;
            this.symbol = symbol;
            this.fee = fee;
        }
    }

    public static final class WalletUpdate {
        final long walletId;
        final BigDecimal balanceDelta;
        final BigDecimal frozenDelta;
        final long version;

        WalletUpdate(Long walletId, BigDecimal balanceDelta, BigDecimal frozenDelta, Long version) {
            this.walletId = walletId;
            this.balanceDelta = balanceDelta;
            this.frozenDelta = frozenDelta;
            this.version = version != null ? version : 0L;
        }
    }
}
