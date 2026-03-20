package com.vaultpi.exchange.service;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.service.WalletService;
import com.vaultpi.exchange.entity.ExchangeOrder;
import com.vaultpi.exchange.event.OrderTradeEvent;
import com.vaultpi.exchange.orderbook.MatchBatchPersistence;
import com.vaultpi.exchange.orderbook.OrderBook;
import com.vaultpi.exchange.repository.ExchangeOrderRepository;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内存订单簿撮合：从数据库加载 TRADING 限价单，撮合在内存完成，成交批次每 100ms 批量落库。
 * 钱包+订单+成交+流水同一事务提交；异常时从订单簿移除并记录指标。
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final ExchangeOrderRepository orderRepository;
    private final ExchangeCoinRepository exchangeCoinRepository;
    private final CoinRepository coinRepository;
    private final MemberWalletRepository walletRepository;
    private final WalletService walletService;
    private final MatchBatchPersistence batchPersistence;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Timer matchDurationTimer;

    /** symbol -> 订单簿，无锁并发由 OrderBook 内部 StampedLock 保证 */
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    /** orderId -> symbol，撤单/异常时 O(1) 定位订单簿 */
    private final Map<String, String> orderIdToSymbol = new ConcurrentHashMap<>();
    /** 是否已执行 loadOrderBooks，供健康检查 */
    private volatile boolean orderBooksLoaded = false;
    private final AtomicBoolean reconcileRunning = new AtomicBoolean(false);

    public MatchService(ExchangeOrderRepository orderRepository,
                        ExchangeCoinRepository exchangeCoinRepository,
                        CoinRepository coinRepository,
                        MemberWalletRepository walletRepository,
                        WalletService walletService,
                        MatchBatchPersistence batchPersistence,
                        ApplicationEventPublisher eventPublisher,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.coinRepository = coinRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.batchPersistence = batchPersistence;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.matchDurationTimer = Timer.builder("vaultpi.match.duration")
            .description("Limit order match attempt duration")
            .register(meterRegistry);
    }

    @PostConstruct
    public void loadOrderBooks() {
        orderBooksLoaded = false;
        try {
            reloadAllOrderBooksFromDb();
            orderBooksLoaded = true;
        } catch (Exception e) {
            meterRegistry.counter("vaultpi.match.orderbooks.load.failures").increment();
            log.error("Failed to load order books from DB, will keep system degraded until reconcile succeeds.", e);
            orderBooksLoaded = false;
        }
    }

    public boolean isOrderBooksLoaded() {
        return orderBooksLoaded;
    }

    private OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    /**
     * 全量重载：清空内存订单簿后重新写入 DB 中所有 TRADING LIMIT 单。
     * 只建议在启动/故障恢复窗口调用。
     */
    private void reloadAllOrderBooksFromDb() {
        orderIdToSymbol.clear();
        for (String sym : new ArrayList<>(orderBooks.keySet())) {
            OrderBook book = orderBooks.get(sym);
            if (book == null) continue;
            long stamp = book.writeLock();
            try {
                book.clear();
            } finally {
                book.unlockWrite(stamp);
            }
        }

        List<ExchangeOrder> trading = orderRepository.findByStatusAndType(ExchangeOrder.STATUS_TRADING, "LIMIT");
        Map<String, List<ExchangeOrder>> bySymbol = new ConcurrentHashMap<>();
        for (ExchangeOrder o : trading) {
            bySymbol.computeIfAbsent(o.getSymbol(), k -> new ArrayList<>()).add(o);
        }

        for (Map.Entry<String, List<ExchangeOrder>> e : bySymbol.entrySet()) {
            String sym = e.getKey();
            OrderBook book = getOrderBook(sym);
            long stamp = book.writeLock();
            try {
                for (ExchangeOrder o : e.getValue()) {
                    book.addOrder(o);
                    orderIdToSymbol.put(o.getOrderId(), sym);
                }
            } finally {
                book.unlockWrite(stamp);
            }
        }
    }

    /**
     * 订单簿一致性巡检：
     * - 内存与 DB 中 TRADING LIMIT 单数量不一致：重建该交易对订单簿
     * - DB 中已无 TRADING 单但内存仍残留：清空该订单簿
     *
     * 用于宕机/异常导致的“幽灵交易对”，从而避免资金长期冻结。
     */
    @Scheduled(fixedDelayString = "${vaultpi.match.orderbooks.reconcile.fixedDelayMs:60000}")
    public void reconcileOrderBooks() {
        if (!reconcileRunning.compareAndSet(false, true)) return;
        try {
            List<ExchangeOrder> trading = orderRepository.findByStatusAndType(ExchangeOrder.STATUS_TRADING, "LIMIT");
            Map<String, List<ExchangeOrder>> bySymbol = new ConcurrentHashMap<>();
            for (ExchangeOrder o : trading) {
                bySymbol.computeIfAbsent(o.getSymbol(), k -> new ArrayList<>()).add(o);
            }

            Set<String> symbolsInDb = bySymbol.keySet();

            // 1) 清空 DB 中已不存在的 symbol 残留
            for (String sym : new ArrayList<>(orderBooks.keySet())) {
                if (symbolsInDb.contains(sym)) continue;
                OrderBook book = orderBooks.get(sym);
                if (book == null) continue;
                long stamp = book.writeLock();
                try {
                    for (String oid : book.snapshotOrderIds()) orderIdToSymbol.remove(oid);
                    book.clear();
                } finally {
                    book.unlockWrite(stamp);
                }
            }

            // 2) 校验并重建数量不一致的 symbol
            for (Map.Entry<String, List<ExchangeOrder>> e : bySymbol.entrySet()) {
                String sym = e.getKey();
                List<ExchangeOrder> orders = e.getValue();
                int dbCount = orders.size();

                OrderBook book = getOrderBook(sym);
                int memCount;
                long r = book.readLock();
                try {
                    memCount = book.getOrderCount();
                } finally {
                    book.unlockRead(r);
                }

                if (memCount == dbCount) continue;

                long stamp = book.writeLock();
                try {
                    for (String oid : book.snapshotOrderIds()) orderIdToSymbol.remove(oid);
                    book.clear();
                    for (ExchangeOrder o : orders) {
                        book.addOrder(o);
                        orderIdToSymbol.put(o.getOrderId(), sym);
                    }
                } finally {
                    book.unlockWrite(stamp);
                }
            }

            orderBooksLoaded = true;
        } catch (Exception ex) {
            meterRegistry.counter("vaultpi.match.orderbooks.reconcile.failures").increment();
            log.error("OrderBooks reconcile failed, system will remain degraded.", ex);
            orderBooksLoaded = false;
        } finally {
            reconcileRunning.set(false);
        }
    }

    /**
     * 撤单时从内存订单簿移除，避免继续被撮合。O(1) 通过 orderIdToSymbol 定位。
     */
    public void removeOrderFromBook(String orderId) {
        String symbol = orderIdToSymbol.remove(orderId);
        if (symbol == null) return;
        OrderBook book = orderBooks.get(symbol);
        if (book == null) return;
        long stamp = book.writeLock();
        try {
            book.removeByOrderId(orderId);
        } finally {
            book.unlockWrite(stamp);
        }
    }

    /** 每 100ms 将成交批次批量写入数据库并在提交后发布事件 */
    @Scheduled(fixedDelay = 100)
    public void flushBatch() {
        batchPersistence.flush();
    }

    public void tryMatch(String orderId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            tryMatchInternal(orderId);
        } finally {
            sample.stop(matchDurationTimer);
        }
    }

    private void tryMatchInternal(String orderId) {
        try {
            tryMatchInternal0(orderId);
        } catch (Exception e) {
            log.error("撮合过程异常，orderId={}", orderId, e);
            meterRegistry.counter("vaultpi.match.exceptions").increment();
            String symbol = orderIdToSymbol.remove(orderId);
            if (symbol != null) {
                OrderBook book = orderBooks.get(symbol);
                if (book != null) {
                    long stamp = book.writeLock();
                    try {
                        book.removeByOrderId(orderId);
                    } finally {
                        book.unlockWrite(stamp);
                    }
                }
            }
        }
    }

    private void tryMatchInternal0(String orderId) {
        ExchangeOrder our = orderRepository.findById(orderId).orElse(null);
        if (our == null || !ExchangeOrder.STATUS_TRADING.equals(our.getStatus()) || !"LIMIT".equals(our.getType())) {
            return;
        }
        Optional<ExchangeCoin> exOpt = exchangeCoinRepository.findBySymbol(our.getSymbol());
        if (exOpt.isEmpty()) return;
        ExchangeCoin ex = exOpt.get();
        Long baseCoinId = coinRepository.findByUnit(ex.getBaseSymbol()).map(Coin::getId).orElse(null);
        Long coinId = coinRepository.findByUnit(ex.getCoinSymbol()).map(Coin::getId).orElse(null);
        if (baseCoinId == null || coinId == null) return;

        // 若启动/巡检过程中内存订单簿不可用，触发一次重建加速恢复（有锁保护，避免并发重建抖动）。
        if (!orderBooksLoaded) {
            meterRegistry.counter("vaultpi.match.orderbooks.reconcile.triggered").increment();
            reconcileOrderBooks();
        }

        BigDecimal ourRemain = our.getAmount().subtract(our.getTradedAmount());
        if (ourRemain.compareTo(BigDecimal.ZERO) <= 0) return;

        OrderBook book = getOrderBook(our.getSymbol());
        long stamp = book.writeLock();
        try {
            book.addOrder(our);
            orderIdToSymbol.put(our.getOrderId(), our.getSymbol());
        } finally {
            book.unlockWrite(stamp);
        }

        while (true) {
            ourRemain = our.getAmount().subtract(our.getTradedAmount());
            if (ourRemain.compareTo(BigDecimal.ZERO) <= 0) break;

            ExchangeOrder other;
            stamp = book.writeLock();
            try {
                other = book.peekBestMatchingOpposite(our);
                if (other == null) break;
                book.removeOrder(other);
            } finally {
                book.unlockWrite(stamp);
            }

            BigDecimal otherRemain = other.getAmount().subtract(other.getTradedAmount());
            if (otherRemain.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal fill = ourRemain.min(otherRemain);
            BigDecimal dealPrice = other.getPrice();
            ExchangeOrder buySide = "BUY".equals(our.getDirection()) ? our : other;
            ExchangeOrder sellSide = "BUY".equals(our.getDirection()) ? other : our;

            boolean success = executeTrade(buySide, sellSide, fill, dealPrice, baseCoinId, coinId);
            if (!success) {
                stamp = book.writeLock();
                try {
                    book.addOrder(other);
                } finally {
                    book.unlockWrite(stamp);
                }
                continue;
            }

            ourRemain = our.getAmount().subtract(our.getTradedAmount());
            if (ourRemain.compareTo(BigDecimal.ZERO) <= 0) {
                stamp = book.writeLock();
                try {
                    book.removeOrder(our);
                    orderIdToSymbol.remove(our.getOrderId());
                } finally {
                    book.unlockWrite(stamp);
                }
                break;
            }
            if (other.getTradedAmount().compareTo(other.getAmount()) < 0) {
                stamp = book.writeLock();
                try {
                    book.addOrder(other);
                } finally {
                    book.unlockWrite(stamp);
                }
            }
        }
    }

    /**
     * 执行成交：校验冻结余额，钱包/订单/成交/流水均入批次，flush() 时同一事务提交。
     * 按 (memberId, coinId) 顺序入队避免死锁；乐观锁版本一并传入。
     * @return true 已入批，false 冻结不足跳过
     */
    private boolean executeTrade(ExchangeOrder buyOrder, ExchangeOrder sellOrder, BigDecimal amount, BigDecimal price,
                                Long baseCoinId, Long coinId) {
        Long buyerId = buyOrder.getMemberId();
        Long sellerId = sellOrder.getMemberId();
        BigDecimal baseAmount = price.multiply(amount);

        MemberWallet buyerBase = walletService.getOrCreateWallet(buyerId, baseCoinId);
        MemberWallet buyerCoin = walletService.getOrCreateWallet(buyerId, coinId);
        MemberWallet sellerBase = walletService.getOrCreateWallet(sellerId, baseCoinId);
        MemberWallet sellerCoin = walletService.getOrCreateWallet(sellerId, coinId);

        if (buyerBase.getFrozenBalance() == null || buyerBase.getFrozenBalance().compareTo(baseAmount) < 0) return false;
        if (sellerCoin.getFrozenBalance() == null || sellerCoin.getFrozenBalance().compareTo(amount) < 0) return false;

        List<WalletDelta> deltas = new ArrayList<>();
        deltas.add(new WalletDelta(buyerBase, BigDecimal.ZERO, baseAmount.negate()));
        deltas.add(new WalletDelta(buyerCoin, amount, BigDecimal.ZERO));
        deltas.add(new WalletDelta(sellerBase, baseAmount, BigDecimal.ZERO));
        deltas.add(new WalletDelta(sellerCoin, BigDecimal.ZERO, amount.negate()));
        deltas.sort(Comparator.comparing(WalletDelta::getMemberId).thenComparing(WalletDelta::getCoinId));
        for (WalletDelta d : deltas) {
            batchPersistence.addWalletUpdate(d.wallet.getId(), d.balanceDelta, d.frozenDelta, d.wallet.getVersion());
        }

        buyOrder.setTradedAmount(buyOrder.getTradedAmount().add(amount));
        sellOrder.setTradedAmount(sellOrder.getTradedAmount().add(amount));
        if (buyOrder.getTradedAmount().compareTo(buyOrder.getAmount()) >= 0) buyOrder.setStatus(ExchangeOrder.STATUS_FILLED);
        if (sellOrder.getTradedAmount().compareTo(sellOrder.getAmount()) >= 0) sellOrder.setStatus(ExchangeOrder.STATUS_FILLED);

        batchPersistence.addOrderUpdate(buyOrder.getOrderId(), buyOrder.getTradedAmount(), buyOrder.getStatus());
        batchPersistence.addOrderUpdate(sellOrder.getOrderId(), sellOrder.getTradedAmount(), sellOrder.getStatus());

        Instant now = Instant.now();
        batchPersistence.addTrade(buyOrder.getSymbol(), "BUY", price, amount, now);

        batchPersistence.addEvent(new OrderTradeEvent(this, buyOrder.getOrderId(), buyerId,
            buyOrder.getSymbol(), amount, price, "BUY"));
        batchPersistence.addEvent(new OrderTradeEvent(this, sellOrder.getOrderId(), sellerId,
            sellOrder.getSymbol(), amount, price, "SELL"));

        String baseUnit = coinRepository.findById(baseCoinId).map(Coin::getUnit).orElse("");
        String coinUnit = coinRepository.findById(coinId).map(Coin::getUnit).orElse("");
        batchPersistence.addTransaction(buyerId, baseAmount.negate(), "TRADE", baseUnit, BigDecimal.ZERO);
        batchPersistence.addTransaction(buyerId, amount, "TRADE", coinUnit, BigDecimal.ZERO);
        batchPersistence.addTransaction(sellerId, baseAmount, "TRADE", baseUnit, BigDecimal.ZERO);
        batchPersistence.addTransaction(sellerId, amount.negate(), "TRADE", coinUnit, BigDecimal.ZERO);
        return true;
    }

    private static final class WalletDelta {
        final MemberWallet wallet;
        final BigDecimal balanceDelta;
        final BigDecimal frozenDelta;

        WalletDelta(MemberWallet wallet, BigDecimal balanceDelta, BigDecimal frozenDelta) {
            this.wallet = wallet;
            this.balanceDelta = balanceDelta;
            this.frozenDelta = frozenDelta;
        }
        long getMemberId() { return wallet.getMemberId(); }
        long getCoinId() { return wallet.getCoinId(); }
    }
}
