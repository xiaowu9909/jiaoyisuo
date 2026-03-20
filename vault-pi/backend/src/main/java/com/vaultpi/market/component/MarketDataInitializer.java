package com.vaultpi.market.component;

import com.vaultpi.admin.dto.TopRealCoinsRequest;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.market.service.TopRealCoinsService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 市场数据初始化：若交易对不足则按 Kraken 实时成交额（turnover）补齐 Top100。
 * 目的：本地 H2 开发态尽量与生产“热门交易对展示策略”一致，并且保持逻辑与生产完全同源。
 */
@Component
public class MarketDataInitializer implements CommandLineRunner {

    private final ExchangeCoinRepository exchangeCoinRepository;
    private final TopRealCoinsService topRealCoinsService;

    public MarketDataInitializer(ExchangeCoinRepository exchangeCoinRepository,
                                 TopRealCoinsService topRealCoinsService) {
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.topRealCoinsService = topRealCoinsService;
    }

    @Override
    public void run(String... args) throws Exception {
        // 只在快照不存在时才会真正打 Kraken；否则复用最新快照并保证本地/生产一致。
        long count = exchangeCoinRepository.count();
        System.out.println("MarketDataInitializer running (exchange_coin.count=" + count + "), applying top-real snapshot...");

        TopRealCoinsRequest req = new TopRealCoinsRequest();
        req.setCount(100);
        req.setDryRun(false);
        topRealCoinsService.applyOrRebuildTopRealCoins(req, true);
    }
}
