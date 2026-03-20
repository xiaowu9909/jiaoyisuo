package com.vaultpi.market.config;

import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.market.service.VirtualMarketEngine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 虚拟盘与趋势相关监控指标，供 Prometheus/Grafana 采集。
 * - 虚拟盘价格偏离率（当前价相对配置中心价）
 * - 虚拟盘数量
 */
@Configuration
public class VirtualMarketMetrics {

    public VirtualMarketMetrics(MeterRegistry registry,
                                VirtualMarketEngine virtualMarketEngine,
                                ExchangeCoinRepository exchangeCoinRepository) {
        List<ExchangeCoin> virtualCoins = exchangeCoinRepository.findByEnableTrue().stream()
            .filter(c -> Boolean.TRUE.equals(c.getVirtual()))
            .toList();
        for (ExchangeCoin coin : virtualCoins) {
            String symbol = coin.getSymbol();
            Gauge.builder("vaultpi.virtual.price.deviation.pct", () -> {
                BigDecimal current = virtualMarketEngine.getCurrentPrice(symbol);
                if (current == null || current.compareTo(BigDecimal.ZERO) <= 0) return Double.NaN;
                BigDecimal center = centerPrice(coin);
                if (center == null || center.compareTo(BigDecimal.ZERO) <= 0) return Double.NaN;
                return current.subtract(center).divide(center, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            })
                .description("Virtual symbol price deviation from config center price (percent)")
                .tags(Tags.of("symbol", symbol))
                .register(registry);
        }
        Gauge.builder("vaultpi.virtual.symbols.count", virtualCoins::size)
            .description("Number of virtual trading pairs")
            .register(registry);
    }

    private static BigDecimal centerPrice(ExchangeCoin c) {
        if (c.getCustomPriceLow() != null && c.getCustomPriceHigh() != null
            && c.getCustomPriceLow().compareTo(c.getCustomPriceHigh()) <= 0)
            return c.getCustomPriceLow().add(c.getCustomPriceHigh()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        return c.getCustomPrice();
    }
}
