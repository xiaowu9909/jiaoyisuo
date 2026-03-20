package com.vaultpi.market.task;

import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.market.service.KrakenMarketRedisService;
import com.vaultpi.market.service.MarketCircuitBreaker;
import com.vaultpi.market.service.MarketLatencyRecorder;
import com.vaultpi.market.service.VirtualMarketEngine;
import com.vaultpi.config.StompSubscriptionAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 每 500ms 从 Redis 读取 Kraken 行情，合并虚拟盘数据，通过 WebSocket 推送给前端。
 * 无 KrakenMarketRedisService（无 Redis）时本 Bean 仍创建，定时任务内直接 no-op。
 */
@Component
public class MarketStreamTask {

    public static final String TOPIC_MARKET_THUMB = "/topic/market-thumb";
    public static final String TOPIC_MARKET_PAIR_THUMB = "/topic/market-pair-thumb";

    private final ExchangeCoinRepository exchangeCoinRepository;
    private final VirtualMarketEngine virtualMarketEngine;
    private final SimpMessagingTemplate messagingTemplate;
    private final KrakenApiClient krakenApiClient;

    // Kraken REST fallback cache：避免每 500ms 都打 Kraken
    private Map<String, Map<String, Object>> restThumbCache = Collections.emptyMap();
    private long restThumbCacheAt = 0;
    // Redis 缺失时用 Kraken REST 补齐；控制 REST 触发频率，避免每 500ms 都打 REST
    private long restThumbCacheTtlMs;

    @Value("${vaultpi.market.rest-thumb-cache-ttl-ms:1000}")
    public void setRestThumbCacheTtlMs(long v) {
        this.restThumbCacheTtlMs = v;
    }

    @Autowired(required = false)
    private KrakenMarketRedisService krakenMarketRedisService;
    @Autowired(required = false)
    private MarketCircuitBreaker marketCircuitBreaker;
    @Autowired(required = false)
    private MarketLatencyRecorder latencyRecorder;

    public MarketStreamTask(ExchangeCoinRepository exchangeCoinRepository,
                            VirtualMarketEngine virtualMarketEngine,
                            SimpMessagingTemplate messagingTemplate,
                            KrakenApiClient krakenApiClient) {
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.virtualMarketEngine = virtualMarketEngine;
        this.messagingTemplate = messagingTemplate;
        this.krakenApiClient = krakenApiClient;
    }

    @Scheduled(fixedRateString = "${vaultpi.market.overview-broadcast-fixed-rate-ms:1000}")
    public void broadcastMarketThumb() {
        long t0 = System.nanoTime();
        List<ExchangeCoin> coins = exchangeCoinRepository.findByEnableTrue();
        if (coins.isEmpty()) return;

        Map<String, Map<String, Object>> redisThumbs = krakenMarketRedisService != null
            ? krakenMarketRedisService.getAllThumbs()
            : Collections.emptyMap();

        // 判断：Redis 中是否缺少部分非虚拟交易对（缺少则用 Kraken REST 补齐）
        boolean needRest = false;
        for (ExchangeCoin c : coins) {
            if (Boolean.TRUE.equals(c.getVirtual())) continue;
            String symbol = c.getSymbol();
            if (!redisThumbs.containsKey(symbol)) {
                needRest = true;
                break;
            }
        }

        long nowMs = System.currentTimeMillis();
        if (needRest && (nowMs - restThumbCacheAt) > restThumbCacheTtlMs) {
            try {
                restThumbCache = krakenApiClient.fetchAllTickers();
                restThumbCacheAt = nowMs;
            } catch (Exception ignored) {
                // 保持旧缓存或空缓存
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();

        for (ExchangeCoin c : coins) {
            String symbol = c.getSymbol();
            Map<String, Object> m = new HashMap<>();
            m.put("symbol", symbol);
            m.put("baseSymbol", c.getBaseSymbol());
            m.put("coinSymbol", c.getCoinSymbol());

            if (Boolean.TRUE.equals(c.getVirtual())) {
                if (!virtualMarketEngine.isVirtual(symbol)) virtualMarketEngine.register(c);
                Map<String, Object> thumb = virtualMarketEngine.getThumb(symbol, c.getBaseSymbol(), c.getCoinSymbol());
                if (thumb != null) m.putAll(thumb);
                else fallbackThumb(m);
            } else {
                Map<String, Object> kData = redisThumbs.get(symbol);
                if (kData != null) {
                    m.put("open", kData.get("open"));
                    m.put("close", kData.get("close"));
                    m.put("high", kData.get("high"));
                    m.put("low", kData.get("low"));
                    m.put("volume", kData.get("volume"));
                    m.put("turnover", kData.get("turnover"));
                    m.put("chg", kData.get("chg"));
                    m.put("change", kData.get("change"));
                } else {
                    Map<String, Object> rData = restThumbCache.get(symbol);
                    if (rData != null) {
                        m.put("open", rData.get("open"));
                        m.put("close", rData.get("close"));
                        m.put("high", rData.get("high"));
                        m.put("low", rData.get("low"));
                        m.put("volume", rData.get("volume"));
                        m.put("turnover", rData.get("turnover"));
                        m.put("chg", rData.get("chg"));
                        m.put("change", rData.get("change"));
                    } else {
                        fallbackThumb(m);
                    }
                }
            }
            list.add(m);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("thumbs", list);
            payload.put("marketMaintenance", marketCircuitBreaker != null && marketCircuitBreaker.isMaintenance());
            messagingTemplate.convertAndSend(TOPIC_MARKET_THUMB, payload);
            if (latencyRecorder != null) latencyRecorder.recordRedisToPush(System.nanoTime() - t0);
        } catch (Exception e) {
            // ignore send errors (e.g. no subscribers)
        }
    }

    @Scheduled(fixedRateString = "${vaultpi.market.active-pair-broadcast-fixed-rate-ms:200}")
    public void broadcastActivePairThumb() {
        Set<String> activePairKeys = StompSubscriptionAuthChannelInterceptor.getActivePairKeys();
        if (activePairKeys.isEmpty()) return;

        // 保护：恶意/异常订阅可能导致 activePairKeys 很大
        int maxPairs = 30;
        int sent = 0;

        for (String pairKey : activePairKeys) {
            if (sent >= maxPairs) break;
            if (pairKey == null || pairKey.isBlank()) continue;

            // pairKey: BTC-USDT => symbol: BTC/USDT
            int idx = pairKey.indexOf('-');
            if (idx <= 0 || idx >= pairKey.length() - 1) continue;
            String symbol = pairKey.substring(0, idx) + "/" + pairKey.substring(idx + 1);

            Map<String, Object> thumb = krakenMarketRedisService != null
                ? krakenMarketRedisService.getThumb(symbol)
                : null;
            if (thumb == null || thumb.isEmpty()) {
                // Redis missing => use REST cache with TTL (throttled)
                long nowMs = System.currentTimeMillis();
                if (restThumbCache == null || restThumbCache.isEmpty() || (nowMs - restThumbCacheAt) > restThumbCacheTtlMs) {
                    try {
                        restThumbCache = krakenApiClient.fetchAllTickers();
                        restThumbCacheAt = nowMs;
                    } catch (Exception ignored) {
                        // keep old cache
                    }
                }
                thumb = restThumbCache.get(symbol);
                if (thumb == null || thumb.isEmpty()) continue;
            }

            Map<String, Object> plate = krakenMarketRedisService != null
                ? krakenMarketRedisService.getPlate(symbol)
                : null;

            // 给前端的 thumb payload：包含 close/chg/open/high/low/volume 等字段
            Map<String, Object> thumbPayload = new HashMap<>(thumb);
            thumbPayload.put("symbol", symbol);

            Map<String, Object> pairPayload = new HashMap<>();
            pairPayload.put("thumb", thumbPayload);
            if (plate != null && !plate.isEmpty()) pairPayload.put("plate", plate);
            pairPayload.put("marketMaintenance", marketCircuitBreaker != null && marketCircuitBreaker.isMaintenance());

            String pairDest = TOPIC_MARKET_PAIR_THUMB + "/" + pairKey;
            try {
                messagingTemplate.convertAndSend(pairDest, pairPayload);
                sent++;
            } catch (Exception ignored) {}
        }
    }

    private void fallbackThumb(Map<String, Object> m) {
        Object sym = m.get("symbol");
        Number fallback = "BTC/USDT".equals(sym) ? 50000 : 3000;
        m.put("open", fallback);
        m.put("close", fallback);
        m.put("high", fallback);
        m.put("low", fallback);
        m.put("chg", 0);
        m.put("change", 0);
        m.put("volume", 0);
        m.put("turnover", 0);
    }
}
