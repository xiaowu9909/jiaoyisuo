package com.vaultpi.market.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpi.admin.dto.TopRealCoinsRequest;
import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.common.Result;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.entity.TopRealCoinsSnapshot;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.market.repository.TopRealCoinsSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 共享服务：从 Kraken 计算“热门实盘 TopN 交易对”，并写入 exchange_coin/coin 表。
 * 也用于本地 H2 初始化和生产环境后台操作，保证两者逻辑一致。
 */
@Service
public class TopRealCoinsService {

    private final ExchangeCoinRepository exchangeCoinRepository;
    private final CoinRepository coinRepository;
    private final KrakenApiClient krakenApiClient;
    private final TopRealCoinsSnapshotRepository snapshotRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TopRealCoinsService(ExchangeCoinRepository exchangeCoinRepository,
                                CoinRepository coinRepository,
                                KrakenApiClient krakenApiClient,
                                TopRealCoinsSnapshotRepository snapshotRepository) {
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.coinRepository = coinRepository;
        this.krakenApiClient = krakenApiClient;
        this.snapshotRepository = snapshotRepository;
    }

    private static boolean isValidBaseSymbol(String base) {
        if (base == null) return false;
        String b = base.trim().toUpperCase(Locale.ROOT);
        if (b.length() < 2 || b.length() > 16) return false;
        return b.matches("^[A-Z][A-Z0-9]*$");
    }

    private static String baseFromSymbol(String symbol) {
        if (symbol == null || !symbol.contains("/")) return null;
        String base = symbol.split("/")[0];
        return base != null ? base.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    public Result<Map<String, Object>> applyOrRebuildTopRealCoins(TopRealCoinsRequest req, boolean calledFromInitializer) {
        int count = req != null && req.getCount() != null ? req.getCount() : 100;
        count = Math.min(200, Math.max(1, count));
        boolean dryRun = req != null && Boolean.TRUE.equals(req.getDryRun());

        // 若手动传入 symbols：直接用这份列表生成快照并应用（用于本地/生产严格一致）
        if (!calledFromInitializer && req != null && req.getSymbols() != null && !req.getSymbols().isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (String sym : req.getSymbols()) {
                if (sym == null) continue;
                String s = sym.trim().toUpperCase(Locale.ROOT);
                if (!s.endsWith("/USDT") || !s.contains("/")) continue;
                String base = baseFromSymbol(s);
                if (!isValidBaseSymbol(base)) continue;
                normalized.add(s);
                if (normalized.size() >= count) break;
            }
            normalized = new ArrayList<>(new LinkedHashSet<>(normalized));
            if (normalized.isEmpty()) return Result.fail(503, "传入 symbols 无效");

            if (dryRun) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("count", normalized.size());
                payload.put("dryRun", true);
                payload.put("symbols", normalized);
                payload.put("appliedSnapshotId", null);
                return Result.ok(payload);
            }

            String symbolsJson;
            try {
                symbolsJson = objectMapper.writeValueAsString(normalized);
            } catch (JsonProcessingException e) {
                return Result.fail(5001, "symbolsJson 序列化失败");
            }
            TopRealCoinsSnapshot snap = new TopRealCoinsSnapshot();
            snap.setCount(normalized.size());
            snap.setSymbolsJson(symbolsJson);
            snap.setCreatedAtMs(System.currentTimeMillis());
            snap = snapshotRepository.save(snap);

            return applySnapshot(snap.getId(), false, symbolsJson);
        }

        // 初始化时：优先复用最新快照；快照不存在才重建（避免本地/生产列表随 Kraken 波动）
        TopRealCoinsSnapshot latest = snapshotRepository.findTopByOrderByIdDesc();
        if (!calledFromInitializer && latest == null) {
            // 手动调用且还没快照：允许继续重建
        }

        // 默认策略：
        // - calledFromInitializer：如果存在快照，直接应用快照（即使 count 不同也照快照走）
        // - 手动调用 rebuild：如果 dryRun，直接返回将要导入 symbols；如果 dryRun=false，则写新快照并应用
        if (calledFromInitializer && latest != null) {
            return applySnapshot(latest.getId(), dryRun, null);
        }

        // 如果快照存在且是 calledFromInitializer=false，但 req 里没指定 dryRun=false：这里仍按“重建”语义执行
        if (dryRun && latest != null && calledFromInitializer) {
            return applySnapshot(latest.getId(), true, latest.getSymbolsJson());
        }

        // 重建：从 Kraken 拉取 tickers，按 turnover 排序取前 count
        Map<String, Map<String, Object>> tickers = krakenApiClient.fetchAllTickers();
        if (tickers == null || tickers.isEmpty()) return Result.fail(503, "Kraken Ticker 不可用");

        List<Map.Entry<String, Map<String, Object>>> sorted = new ArrayList<>(tickers.entrySet());
        sorted.sort((a, b) -> toBigDecimal(b.getValue().get("turnover")).compareTo(toBigDecimal(a.getValue().get("turnover"))));

        List<String> selected = new ArrayList<>();
        for (var e : sorted) {
            String symbol = e.getKey();
            if (symbol == null) continue;
            String s = symbol.trim().toUpperCase(Locale.ROOT);
            // 只接受 BASE/USDT
            if (!s.endsWith("/USDT")) continue;
            if (!s.contains("/")) continue;
            String base = baseFromSymbol(s);
            if (!isValidBaseSymbol(base)) continue;
            selected.add(s);
            if (selected.size() >= count) break;
        }

        selected = new ArrayList<>(new LinkedHashSet<>(selected)); // 去重保持顺序
        if (selected.size() > count) selected = selected.subList(0, count);

        if (selected.isEmpty()) return Result.fail(5001, "未找到可用 USDT 交易对");

        if (dryRun) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("count", selected.size());
            payload.put("dryRun", true);
            payload.put("symbols", selected);
            payload.put("appliedSnapshotId", latest != null ? latest.getId() : null);
            return Result.ok(payload);
        }

        // 写新快照并应用
        String symbolsJson;
        try {
            symbolsJson = objectMapper.writeValueAsString(selected);
        } catch (JsonProcessingException e) {
            return Result.fail(5001, "symbolsJson 序列化失败");
        }

        TopRealCoinsSnapshot snap = new TopRealCoinsSnapshot();
        snap.setCount(selected.size());
        snap.setSymbolsJson(symbolsJson);
        snap.setCreatedAtMs(System.currentTimeMillis());
        snap = snapshotRepository.save(snap);

        return applySnapshot(snap.getId(), false, symbolsJson);
    }

    @Transactional
    public Result<Map<String, Object>> applySnapshot(Long snapshotId, boolean dryRun, String overrideSymbolsJson) {
        if (snapshotId == null && overrideSymbolsJson == null) return Result.fail(5001, "snapshotId 不能为空");
        TopRealCoinsSnapshot snap = null;
        if (overrideSymbolsJson == null) snap = snapshotRepository.findById(snapshotId).orElse(null);
        if (snap == null && overrideSymbolsJson == null) return Result.fail(404, "快照不存在");
        String symbolsJson = overrideSymbolsJson != null ? overrideSymbolsJson : snap.getSymbolsJson();

        List<String> symbols;
        try {
            symbols = objectMapper.readValue(symbolsJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return Result.fail(5001, "symbolsJson 解析失败");
        }
        if (symbols == null) symbols = List.of();

        if (dryRun) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("count", symbols.size());
            payload.put("dryRun", true);
            payload.put("symbols", symbols);
            payload.put("appliedSnapshotId", snapshotId);
            return Result.ok(payload);
        }

        Set<String> selectedSet = new HashSet<>(symbols);

        // 保证 USDT 交易对 base 的币种存在
        coinRepository.findByUnit("USDT").ifPresentOrElse(existing -> {
            if (!Boolean.TRUE.equals(existing.getEnable())) {
                existing.setEnable(true);
                coinRepository.save(existing);
            }
        }, () -> {
            Coin coin = new Coin();
            coin.setUnit("USDT");
            coin.setName("USDT");
            coin.setEnable(true);
            coinRepository.save(coin);
        });

        List<ExchangeCoin> all = exchangeCoinRepository.findAll();
        Map<String, ExchangeCoin> existingBySymbol = new HashMap<>();
        for (ExchangeCoin c : all) {
            if (c == null || Boolean.TRUE.equals(c.getVirtual())) continue;
            if (c.getSymbol() != null) existingBySymbol.put(c.getSymbol(), c);
        }

        // 先设置 enable=false（只影响非虚拟盘）
        for (ExchangeCoin c : all) {
            if (c == null || Boolean.TRUE.equals(c.getVirtual())) continue;
            boolean shouldEnable = selectedSet.contains(c.getSymbol());
            c.setEnable(shouldEnable);
            if (shouldEnable) {
                c.setVirtual(false);
                c.setBaseSymbol("USDT");
                c.setCoinSymbol(baseFromSymbol(c.getSymbol()));
            }
        }
        exchangeCoinRepository.saveAll(all);

        // 再对新 symbol 确保存在且写正确 base/coinSymbol
        for (String sym : symbols) {
            String base = baseFromSymbol(sym);
            if (base == null) continue;

            // coin 表补齐
            coinRepository.findByUnit(base).ifPresentOrElse(existing -> {
                if (!Boolean.TRUE.equals(existing.getEnable())) {
                    existing.setEnable(true);
                    coinRepository.save(existing);
                }
            }, () -> {
                Coin coin = new Coin();
                coin.setUnit(base);
                coin.setName(base);
                coin.setEnable(true);
                coinRepository.save(coin);
            });

            ExchangeCoin exist = existingBySymbol.get(sym);
            if (exist != null) {
                exist.setEnable(true);
                exist.setVirtual(false);
                exist.setBaseSymbol("USDT");
                exist.setCoinSymbol(base);
                exchangeCoinRepository.save(exist);
            } else {
                ExchangeCoin c = new ExchangeCoin();
                c.setSymbol(sym);
                c.setBaseSymbol("USDT");
                c.setCoinSymbol(base);
                c.setEnable(true);
                c.setVirtual(false);
                exchangeCoinRepository.save(c);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("count", symbols.size());
        payload.put("dryRun", false);
        payload.put("symbols", symbols);
        payload.put("appliedSnapshotId", snapshotId);
        return Result.ok(payload);
    }
}

