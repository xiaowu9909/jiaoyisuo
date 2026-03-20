package com.vaultpi.exchange.orderbook;

import com.vaultpi.exchange.entity.ExchangeOrder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.locks.StampedLock;

/**
 * 内存订单簿：买盘/卖盘按价格档位 + 同价 FIFO。
 * 使用 StampedLock 支持多读单写，撮合时持写锁保证一致性。
 */
public class OrderBook {

    private final String symbol;
    /** 买盘：价格降序（最高买价优先），同价 FIFO */
    private final TreeMap<BigDecimal, Deque<ExchangeOrder>> bids;
    /** 卖盘：价格升序（最低卖价优先），同价 FIFO */
    private final TreeMap<BigDecimal, Deque<ExchangeOrder>> asks;
    /** orderId -> 价格，用于撤单/移除时快速定位 */
    private final Map<String, BigDecimal> orderIdToPrice = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    public OrderBook(String symbol) {
        this.symbol = Objects.requireNonNull(symbol);
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>();
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * 将订单加入订单簿（仅 LIMIT + TRADING）。调用方需持写锁。
     */
    public void addOrder(ExchangeOrder order) {
        if (order == null || !ExchangeOrder.STATUS_TRADING.equals(order.getStatus())
            || !"LIMIT".equals(order.getType())) {
            return;
        }
        BigDecimal price = order.getPrice();
        if ("BUY".equals(order.getDirection())) {
            bids.computeIfAbsent(price, k -> new LinkedList<>()).addLast(order);
        } else {
            asks.computeIfAbsent(price, k -> new LinkedList<>()).addLast(order);
        }
        orderIdToPrice.put(order.getOrderId(), price);
    }

    /**
     * 从订单簿移除订单。调用方需持写锁。
     */
    public void removeOrder(ExchangeOrder order) {
        if (order == null) return;
        removeByOrderId(order.getOrderId());
    }

    /**
     * 按 orderId 移除（用于撤单）。调用方需持写锁。
     */
    public boolean removeByOrderId(String orderId) {
        BigDecimal price = orderIdToPrice.remove(orderId);
        if (price == null) return false;
        Deque<ExchangeOrder> bidDeque = bids.get(price);
        if (bidDeque != null && removeFromDeque(bidDeque, orderId)) {
            if (bidDeque.isEmpty()) bids.remove(price);
            return true;
        }
        Deque<ExchangeOrder> askDeque = asks.get(price);
        if (askDeque != null && removeFromDeque(askDeque, orderId)) {
            if (askDeque.isEmpty()) asks.remove(price);
            return true;
        }
        return false;
    }

    private static boolean removeFromDeque(Deque<ExchangeOrder> deque, String orderId) {
        Iterator<ExchangeOrder> it = deque.iterator();
        while (it.hasNext()) {
            if (orderId.equals(it.next().getOrderId())) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * 获取可撮合的对向最优订单（不移除）。调用方需持读锁或写锁。
     * BUY 时返回最优卖单（最低卖价且 price <= ourPrice）；SELL 时返回最优买单（最高买价且 price >= ourPrice）。
     */
    public ExchangeOrder peekBestMatchingOpposite(ExchangeOrder our) {
        if (our == null) return null;
        BigDecimal ourPrice = our.getPrice();
        BigDecimal ourRemain = our.getAmount().subtract(our.getTradedAmount());
        if (ourRemain.compareTo(BigDecimal.ZERO) <= 0) return null;

        if ("BUY".equals(our.getDirection())) {
            Map.Entry<BigDecimal, Deque<ExchangeOrder>> entry = asks.floorEntry(ourPrice);
            if (entry == null) return null;
            ExchangeOrder first = entry.getValue().peekFirst();
            if (first == null) return null;
            if (our.getMemberId().equals(first.getMemberId())) return null;
            BigDecimal otherRemain = first.getAmount().subtract(first.getTradedAmount());
            return otherRemain.compareTo(BigDecimal.ZERO) > 0 ? first : null;
        } else {
            Map.Entry<BigDecimal, Deque<ExchangeOrder>> entry = bids.ceilingEntry(ourPrice);
            if (entry == null) return null;
            ExchangeOrder first = entry.getValue().peekFirst();
            if (first == null) return null;
            if (our.getMemberId().equals(first.getMemberId())) return null;
            BigDecimal otherRemain = first.getAmount().subtract(first.getTradedAmount());
            return otherRemain.compareTo(BigDecimal.ZERO) > 0 ? first : null;
        }
    }

    /**
     * 持写锁执行操作（用于撮合：加单、多笔撮合、移除）。
     */
    public long writeLock() {
        return lock.writeLock();
    }

    public void unlockWrite(long stamp) {
        lock.unlockWrite(stamp);
    }

    /**
     * 持读锁执行操作（用于只读快照，如深度）。
     */
    public long readLock() {
        return lock.readLock();
    }

    public void unlockRead(long stamp) {
        lock.unlockRead(stamp);
    }

    /**
     * 当前订单数量（建议在调用方已持有锁时使用）。
     */
    public int getOrderCount() {
        return orderIdToPrice.size();
    }

    /**
     * 返回当前订单簿中所有 orderId 的快照（建议在调用方已持有锁时使用）。
     */
    public Set<String> snapshotOrderIds() {
        return new HashSet<>(orderIdToPrice.keySet());
    }

    /**
     * 清空订单簿（建议在调用方已持有 writeLock 时使用）。
     */
    public void clear() {
        bids.clear();
        asks.clear();
        orderIdToPrice.clear();
    }
}
