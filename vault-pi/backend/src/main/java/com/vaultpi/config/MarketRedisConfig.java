package com.vaultpi.config;

import com.vaultpi.market.service.KrakenMarketRedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 显式注册 Kraken 行情 Redis 服务，确保在 com.vaultpi 扫描路径下被加载。
 * 仅当存在 StringRedisTemplate（Redis 已配置）时创建。
 */
@Configuration
public class MarketRedisConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public KrakenMarketRedisService krakenMarketRedisService(StringRedisTemplate redis) {
        return new KrakenMarketRedisService(redis);
    }
}
