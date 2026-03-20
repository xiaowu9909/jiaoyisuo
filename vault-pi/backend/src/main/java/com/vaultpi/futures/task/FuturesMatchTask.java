package com.vaultpi.futures.task;

import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.market.service.KrakenMarketRedisService;
import com.vaultpi.market.service.KrakenWebSocketRunner;
import com.vaultpi.futures.entity.ContractOrder;
import com.vaultpi.futures.entity.ContractPosition;
import com.vaultpi.futures.repository.ContractOrderRepository;
import com.vaultpi.futures.repository.ContractPositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FuturesMatchTask {

    private static final int SMA_WINDOW = 10;

    private final ContractOrderRepository orderRepository;
    private final ContractPositionRepository positionRepository;
    private final KrakenApiClient krakenApiClient;
    private final KrakenMarketRedisService krakenMarketRedisService;
    private final KrakenWebSocketRunner krakenWebSocketRunner;

    /** symbol -> 最近 N 个价格，用于 SMA 防插针 */
    private final Map<String, Deque<BigDecimal>> priceHistoryBySymbol = new ConcurrentHashMap<>();

    public FuturesMatchTask(ContractOrderRepository orderRepository,
                            ContractPositionRepository positionRepository,
                            KrakenApiClient krakenApiClient,
                            @Autowired(required = false) KrakenMarketRedisService krakenMarketRedisService,
                            @Autowired(required = false) KrakenWebSocketRunner krakenWebSocketRunner) {
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.krakenApiClient = krakenApiClient;
        this.krakenMarketRedisService = krakenMarketRedisService;
        this.krakenWebSocketRunner = krakenWebSocketRunner;
    }

    /**
     * 获取用于撮合的价格：优先 Redis(WS)，降级 REST；并做 SMA 平滑防插针。
     */
    private BigDecimal getPriceForMatch(String symbol) {
        BigDecimal raw = null;
        if (krakenMarketRedisService != null) {
            Map<String, Object> thumb = krakenMarketRedisService.getThumb(symbol);
            if (thumb != null && thumb.get("close") != null) {
                raw = KrakenMarketRedisService.toBigDecimal(thumb.get("close"));
            }
        }
        if (raw == null || raw.compareTo(BigDecimal.ZERO) == 0) {
            if (krakenWebSocketRunner != null) {
                log.error("Kraken WS unavailable or Redis missing price for {}, falling back to REST (wsConnected={})",
                    symbol, krakenWebSocketRunner.isConnected());
            }
            raw = krakenApiClient.fetchCurrentPrice(symbol);
        }
        if (raw == null || raw.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        Deque<BigDecimal> q = priceHistoryBySymbol.computeIfAbsent(symbol, k -> new LinkedList<>());
        q.addLast(raw);
        while (q.size() > SMA_WINDOW) q.removeFirst();
        if (q.isEmpty()) return raw;
        BigDecimal sum = q.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(q.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * Executes every 5 seconds to match PENDING simulated orders against Kraken price (SMA-smoothed).
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void matchPendingOrders() {
        List<ContractOrder> pendingOrders = orderRepository.findByStatus("PENDING");
        if (pendingOrders.isEmpty()) return;

        pendingOrders.stream().map(ContractOrder::getSymbol).distinct().forEach(symbol -> {
            BigDecimal currentPrice = getPriceForMatch(symbol);
            if (currentPrice.compareTo(BigDecimal.ZERO) == 0) return;

            List<ContractOrder> symbolOrders = pendingOrders.stream()
                .filter(o -> o.getSymbol().equals(symbol))
                .toList();

            for (ContractOrder order : symbolOrders) {
                boolean match = false;
                if ("MARKET".equals(order.getType())) {
                    match = true;
                } else if ("LIMIT".equals(order.getType())) {
                    if ("LONG".equals(order.getDirection()) && currentPrice.compareTo(order.getPrice()) <= 0) {
                        match = true;
                    } else if ("SHORT".equals(order.getDirection()) && currentPrice.compareTo(order.getPrice()) >= 0) {
                        match = true;
                    }
                }

                if (match) {
                    executeOrder(order, currentPrice);
                }
            }
        });
    }

    private void executeOrder(ContractOrder order, BigDecimal executionPrice) {
        order.setStatus("COMPLETED");
        orderRepository.save(order);

        // Check if there's already an OPEN position for this member + symbol + direction
        Optional<ContractPosition> existingOpt = positionRepository.findByMemberIdAndStatus(order.getMemberId(), "OPEN").stream()
                .filter(p -> p.getSymbol().equals(order.getSymbol()) && p.getDirection().equals(order.getDirection()))
                .findFirst();

        ContractPosition position;
        if (existingOpt.isPresent()) {
            position = existingOpt.get();
            // Calculate new average price
            // newAvg = (oldVolume * oldAvg + newVolume * execPrice) / (oldVolume + newVolume)
            BigDecimal notionalOld = position.getVolume().multiply(position.getAvgPrice());
            BigDecimal notionalNew = order.getAmount().multiply(executionPrice);
            BigDecimal totalVolume = position.getVolume().add(order.getAmount());
            BigDecimal newAvg = notionalOld.add(notionalNew).divide(totalVolume, 16, java.math.RoundingMode.HALF_UP);
            
            position.setAvgPrice(newAvg);
            position.setVolume(totalVolume);
            position.setMargin(position.getMargin().add(order.getMargin()));
            // Keep the leverage of the existing position or overwrite it? Overwriting for simplicity in simulation.
            position.setLeverage(order.getLeverage());
        } else {
            position = new ContractPosition();
            position.setMemberId(order.getMemberId());
            position.setSymbol(order.getSymbol());
            position.setDirection(order.getDirection());
            position.setLeverage(order.getLeverage());
            position.setAvgPrice(executionPrice);
            position.setVolume(order.getAmount());
            position.setMargin(order.getMargin());
            position.setStatus("OPEN");
            position.setCreateTime(Instant.now());
        }
        
        positionRepository.save(position);
        log.info("Simulated Order {} Executed! Created/Updated Position ID {}", order.getOrderId(), position.getId());
    }

}
