package com.vaultpi.futures.task;

import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.futures.entity.ContractPosition;
import com.vaultpi.futures.repository.ContractPositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FuturesLiquidationTask {

    private final ContractPositionRepository positionRepository;
    private final KrakenApiClient krakenApiClient;

    public FuturesLiquidationTask(ContractPositionRepository positionRepository,
                                  KrakenApiClient krakenApiClient) {
        this.positionRepository = positionRepository;
        this.krakenApiClient = krakenApiClient;
    }

    /**
     * Executes every 5 seconds to scan OPEN positions for liquidation
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void checkLiquidations() {
        List<ContractPosition> openPositions = positionRepository.findByStatus("OPEN");
        if (openPositions.isEmpty()) return;

        openPositions.stream().map(ContractPosition::getSymbol).distinct().forEach(symbol -> {
            BigDecimal currentPrice = krakenApiClient.fetchCurrentPrice(symbol);
            if (currentPrice.compareTo(BigDecimal.ZERO) == 0) return;

            openPositions.stream()
                .filter(p -> p.getSymbol().equals(symbol))
                .forEach(pos -> {
                    BigDecimal pnl;
                    if ("LONG".equals(pos.getDirection())) {
                        pnl = currentPrice.subtract(pos.getAvgPrice()).multiply(pos.getVolume());
                    } else {
                        pnl = pos.getAvgPrice().subtract(currentPrice).multiply(pos.getVolume());
                    }

                    BigDecimal equity = pos.getMargin().add(pnl);

                    // Simplistic liquidation criteria: If equity <= 0, liquidate
                    if (equity.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("Liquidating Position {} | Margin {} | PNL {}", pos.getId(), pos.getMargin(), pnl);
                        pos.setStatus("LIQUIDATED");
                        // User loses the entire margin deposit. PNL mathematically means margin is completely consumed.
                        pos.setRealizedPnl(pos.getMargin().negate());
                        positionRepository.save(pos);
                    }
                });
        });
    }

}
