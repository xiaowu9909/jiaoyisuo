package com.vaultpi.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 虚拟盘专属行情引擎：GBM 价格 + 中心辐射深度(带呼吸) + 成交-价格联动。
 * 每 100ms 步进 GBM 并推送价格；深度每 300ms 呼吸一次。
 */
@Service
public class VirtualMarketEngine {

    private static final Logger log = LoggerFactory.getLogger(VirtualMarketEngine.class);

    private static final long STEP_MS = 100L;
    private static final long DAY_MS = 86400_000L;
    private static final double DEFAULT_DRIFT_DAILY = 0.0;
    private static final double DEFAULT_VOLATILITY = 0.015;
    private static final BigDecimal DEFAULT_TICK = new BigDecimal("0.0001");
    /** 最近成交条数（控制内存，多虚拟盘 × 500 易 OOM） */
    private static final int MAX_TRADES = 200;
    /** 每周期 K 线历史条数上限（completedBars 滚动保留，降低 50 盘 × 4 周期 × 500 对象规模） */
    private static final int MAX_COMPLETED_BARS_CAP = 200;
    private static final int MAX_DEPTH = 50;
    private static final double BREATH_PROB = 0.15;
    private static final BigDecimal BIG_MOVE_THRESHOLD = new BigDecimal("0.002");

    @Value("${vaultpi.virtual.kline-history-bars:500}")
    private int klineHistoryBars;
    @Value("${vaultpi.virtual.default-price-low:60}")
    private double defaultPriceLow;
    @Value("${vaultpi.virtual.default-price-high:120}")
    private double defaultPriceHigh;
    @Value("${vaultpi.virtual.entropy-noise-enabled:true}")
    private boolean entropyNoiseEnabled;
    @Value("${vaultpi.virtual.dynamic-volatility-enabled:true}")
    private boolean dynamicVolatilityEnabled;
    @Value("${vaultpi.virtual.ou-mode-enabled:true}")
    private boolean ouModeEnabled;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** OU 过程均值回归强度（相对日线） */
    private static final double OU_THETA = 2.0;
    private static final String REDIS_KEY_PREFIX = "virtual:state:";
    private static final long REDIS_TTL_SECONDS = 300L;
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    private final ExchangeCoinRepository exchangeCoinRepository;
    private final MeterRegistry meterRegistry;

    private final Counter snapshotRedisFailures;
    private final Counter broadcastFailures;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /** symbol -> state (price, 24h, depth, trades, bars) */
    private final Map<String, VirtualState> stateBySymbol = new ConcurrentHashMap<>();
    /** symbol -> coin config (for drift/volatility/tickSize/activity) */
    private final Map<String, ExchangeCoin> coinBySymbol = new ConcurrentHashMap<>();

    public VirtualMarketEngine(ExchangeCoinRepository exchangeCoinRepository, MeterRegistry meterRegistry) {
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.meterRegistry = meterRegistry;
        this.snapshotRedisFailures = Counter.builder("vaultpi.virtual.snapshot.redis.failures")
            .description("Redis atomic snapshot write failures for virtual market")
            .register(meterRegistry);
        this.broadcastFailures = Counter.builder("vaultpi.virtual.broadcast.failures")
            .description("WebSocket broadcast failures for virtual prices")
            .register(meterRegistry);
    }

    @PostConstruct
    public void loadVirtualCoins() {
        List<ExchangeCoin> coins = exchangeCoinRepository.findByEnableTrue().stream()
            .filter(c -> Boolean.TRUE.equals(c.getVirtual()))
            .toList();
        for (ExchangeCoin c : coins) {
            if (!tryRestoreFromRedis(c)) {
                register(c);
            }
        }
    }

    /** 从 Redis 恢复虚拟盘状态（仅恢复价格与 24h 统计，深度/K 线按现价重建），失败或未配置 Redis 时返回 false */
    private boolean tryRestoreFromRedis(ExchangeCoin coin) {
        if (redisTemplate == null) return false;
        String symbol = coin.getSymbol();
        String key = REDIS_KEY_PREFIX + symbol;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return false;
            Map<String, Object> map = SNAPSHOT_MAPPER.readValue(json, new TypeReference<>() {});
            BigDecimal price = parseBigDecimal(map.get("price"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return false;
            VirtualState s = new VirtualState();
            s.price = price;
            s.open24h = parseBigDecimal(map.get("open24h"));
            if (s.open24h == null) s.open24h = price;
            s.high24h = parseBigDecimal(map.get("high24h"));
            if (s.high24h == null) s.high24h = price;
            s.low24h = parseBigDecimal(map.get("low24h"));
            if (s.low24h == null) s.low24h = price;
            s.volume24h = parseBigDecimal(map.get("volume24h"));
            if (s.volume24h == null) s.volume24h = BigDecimal.ZERO;
            Object last = map.get("lastStepMs");
            s.lastStepMs = last instanceof Number ? ((Number) last).longValue() : System.currentTimeMillis();
            s.tickSize = tickSize(coin);
            s.driftDaily = driftDaily(coin);
            s.volatility = volatility(coin);
            s.activity = coin.getVirtualActivity() != null ? coin.getVirtualActivity() : "NORMAL";
            buildDepth(s);
            initBarAggregators(s, symbol);
            stateBySymbol.put(symbol, s);
            coinBySymbol.put(symbol, coin);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static BigDecimal parseBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** 每 30 秒将虚拟盘状态快照到 Redis（TTL 5 分钟），便于重启后恢复、多实例共享 */
    @Scheduled(fixedRate = 30_000)
    public void snapshotToRedis() {
        if (redisTemplate == null) return;
        List<String> keys = new ArrayList<>();
        List<String> argv = new ArrayList<>();

        for (Map.Entry<String, VirtualState> e : stateBySymbol.entrySet()) {
            String symbol = e.getKey();
            VirtualState s = e.getValue();
            try {
                String json;
                synchronized (s) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("price", s.price != null ? s.price.toPlainString() : null);
                    map.put("open24h", s.open24h != null ? s.open24h.toPlainString() : null);
                    map.put("high24h", s.high24h != null ? s.high24h.toPlainString() : null);
                    map.put("low24h", s.low24h != null ? s.low24h.toPlainString() : null);
                    map.put("volume24h", s.volume24h != null ? s.volume24h.toPlainString() : null);
                    map.put("lastStepMs", s.lastStepMs);
                    json = SNAPSHOT_MAPPER.writeValueAsString(map);
                }
                keys.add(REDIS_KEY_PREFIX + symbol);
                argv.add(json);
            } catch (Exception ex) {
                snapshotRedisFailures.increment();
                // 单个 symbol snapshot 构建失败不应阻断整体脚本写入
                // 这里只打 warn：避免 50+ 虚拟盘时刷屏
                log.warn("VirtualMarketEngine snapshotToRedis json build failed: {}", ex.toString());
            }
        }

        if (keys.isEmpty()) return;

        // Redis Lua 脚本：一次性 SET 多个 key，并统一 TTL，避免多 key 写入期间不完整快照
        final String lua = ""
            + "local ttl = tonumber(ARGV[#ARGV])\n"
            + "for i=1,#KEYS do\n"
            + "  redis.call('SET', KEYS[i], ARGV[i], 'EX', ttl)\n"
            + "end\n"
            + "return 1\n";

        argv.add(String.valueOf(REDIS_TTL_SECONDS));
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(lua, Long.class);

        try {
            redisTemplate.execute(script, keys, argv.toArray());
        } catch (Exception e) {
            snapshotRedisFailures.increment();
            // 同步打出关键上下文，便于排查脚本执行失败
            log.warn("VirtualMarketEngine snapshotToRedis lua execute failed: keys={}, err={}", keys.size(), e.toString());
        }
    }

    public void register(ExchangeCoin coin) {
        if (!Boolean.TRUE.equals(coin.getVirtual())) return;
        String symbol = coin.getSymbol();
        BigDecimal initPrice = virtualCenterPrice(coin);
        if (initPrice == null || initPrice.compareTo(BigDecimal.ZERO) <= 0) initPrice = BigDecimal.ONE;
        VirtualState s = stateBySymbol.get(symbol);
        if (s != null) {
            synchronized (s) {
                s.driftDaily = driftDaily(coin);
                s.volatility = volatility(coin);
                s.tickSize = tickSize(coin);
                s.activity = coin.getVirtualActivity() != null ? coin.getVirtualActivity() : "NORMAL";
            }
        } else {
            final BigDecimal finalInitPrice = initPrice;
            stateBySymbol.computeIfAbsent(symbol, k -> {
                VirtualState ns = new VirtualState();
                ns.price = finalInitPrice;
                ns.open24h = finalInitPrice;
                ns.high24h = finalInitPrice;
                ns.low24h = finalInitPrice;
                ns.volume24h = BigDecimal.ZERO;
                ns.lastStepMs = System.currentTimeMillis();
                ns.tickSize = tickSize(coin);
                ns.driftDaily = driftDaily(coin);
                ns.volatility = volatility(coin);
                ns.activity = coin.getVirtualActivity() != null ? coin.getVirtualActivity() : "NORMAL";
                buildDepth(ns);
                initBarAggregators(ns, symbol);
                return ns;
            });
        }
        coinBySymbol.put(symbol, coin);
    }

    /** 更新引擎内缓存的 coin（如管理端修改趋势后需调用，使 trend 生效） */
    public void updateCoin(ExchangeCoin coin) {
        if (coin == null || !Boolean.TRUE.equals(coin.getVirtual())) return;
        coinBySymbol.put(coin.getSymbol(), coin);
        VirtualState s = stateBySymbol.get(coin.getSymbol());
        if (s != null) {
            synchronized (s) {
                s.driftDaily = driftDaily(coin);
                s.volatility = volatility(coin);
                s.tickSize = tickSize(coin);
                s.activity = coin.getVirtualActivity() != null ? coin.getVirtualActivity() : "NORMAL";
            }
        }
    }

    private static BigDecimal virtualCenterPrice(ExchangeCoin c) {
        if (c.getCustomPriceLow() != null && c.getCustomPriceHigh() != null && c.getCustomPriceLow().compareTo(c.getCustomPriceHigh()) <= 0)
            return c.getCustomPriceLow().add(c.getCustomPriceHigh()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        if (c.getCustomPrice() != null) return c.getCustomPrice();
        return null;
    }

    private double driftDaily(ExchangeCoin c) {
        if (c.getVirtualDriftDaily() != null) return c.getVirtualDriftDaily().doubleValue();
        return DEFAULT_DRIFT_DAILY;
    }

    private double volatility(ExchangeCoin c) {
        if (c.getVirtualVolatility() != null && c.getVirtualVolatility().doubleValue() > 0)
            return c.getVirtualVolatility().doubleValue();
        return DEFAULT_VOLATILITY;
    }

    private BigDecimal tickSize(ExchangeCoin c) {
        if (c.getVirtualTickSize() != null && c.getVirtualTickSize().compareTo(BigDecimal.ZERO) > 0)
            return c.getVirtualTickSize();
        return DEFAULT_TICK;
    }

    /** 每 100ms 步进 GBM（fixedDelay 防止堆积：上次执行完后再等 100ms，避免线程饥饿/死锁） */
    @Scheduled(fixedDelay = 100)
    public void stepGbm() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, VirtualState> e : stateBySymbol.entrySet()) {
            String symbol = e.getKey();
            VirtualState s = e.getValue();
            synchronized (s) {
                BigDecimal oldPrice = s.price;
                double driftDt = s.driftDaily * (STEP_MS / (double) DAY_MS);
                double vol = s.volatility * s.volatilityMultiplier;
                double volDt = vol * Math.sqrt(STEP_MS / (double) DAY_MS);
                double z = ThreadLocalRandom.current().nextGaussian();
                if (entropyNoiseEnabled) {
                    double noise = (SECURE_RANDOM.nextDouble() * 2 - 1) * 0.002;
                    z += noise;
                }
                double factor;
                if (ouModeEnabled && s.useOu && s.open24h != null && s.open24h.compareTo(BigDecimal.ZERO) > 0
                        && oldPrice != null && oldPrice.compareTo(BigDecimal.ZERO) > 0) {
                    double mean = s.open24h.doubleValue();
                    double x = oldPrice.doubleValue();
                    if (x <= 0) {
                        factor = 1.0 + driftDt + volDt * z;
                    } else {
                        double dt = STEP_MS / (double) DAY_MS;
                        double dx = OU_THETA * (mean - x) * dt + volDt * z;
                        factor = 1.0 + (dx / x);
                    }
                } else {
                    factor = 1.0 + driftDt + volDt * z;
                }
                if (factor <= 0 || Double.isNaN(factor)) factor = 0.999;
                s.price = oldPrice.multiply(BigDecimal.valueOf(factor)).setScale(8, RoundingMode.HALF_UP);
                if (s.price.compareTo(BigDecimal.ZERO) <= 0) s.price = oldPrice;
                // 边界钳位：防止 double 精度累积导致长期漂移至 0 或无穷，运行一周仍落在配置区间
                BigDecimal lo = BigDecimal.valueOf(defaultPriceLow);
                BigDecimal hi = BigDecimal.valueOf(defaultPriceHigh);
                if (s.price.compareTo(lo) < 0) s.price = lo;
                if (s.price.compareTo(hi) > 0) s.price = hi;

                // 行情趋势：周期内既有上涨也有下跌的波动，整体在结束时达到预设涨跌幅；弱拉力 + GBM 主导
                ExchangeCoin coin = coinBySymbol.get(symbol);
                if (coin != null && coin.getTrendDirection() != null && coin.getTrendPercent() != null
                        && coin.getTrendDuration() != null && coin.getTrendStartTime() != null && coin.getTrendStartPrice() != null) {
                    long startMs = coin.getTrendStartTime();
                    int durationSec = coin.getTrendDuration();
                    long endMs = startMs + durationSec * 1000L;
                    if (now < endMs && durationSec > 0) {
                        double elapsedSec = (now - startMs) / 1000.0;
                        double progress = Math.min(1.0, elapsedSec / durationSec);
                        double pct = coin.getTrendPercent().doubleValue() / 100.0;
                        int dir = "UP".equalsIgnoreCase(coin.getTrendDirection()) ? 1 : -1;
                        BigDecimal target = coin.getTrendStartPrice().multiply(BigDecimal.valueOf(1 + dir * pct * progress)).setScale(8, RoundingMode.HALF_UP);
                        // 弱拉力：连续函数 k(remainingRatio)，避免 0.15 处跳跃；越接近结束 k 略增
                        double remainingRatio = 1.0 - progress;
                        double k = 0.006 + 0.014 * remainingRatio;
                        k = Math.min(0.02, Math.max(0.003, k));
                        s.price = s.price.multiply(BigDecimal.valueOf(1 - k)).add(target.multiply(BigDecimal.valueOf(k))).setScale(8, RoundingMode.HALF_UP);
                        if (s.price.compareTo(BigDecimal.ZERO) <= 0) s.price = oldPrice;
                        if (s.price.compareTo(lo) < 0) s.price = lo;
                        if (s.price.compareTo(hi) > 0) s.price = hi;
                    }
                }

                long dayBoundary = (s.lastStepMs / DAY_MS) * DAY_MS;
                long dayBoundaryNow = (now / DAY_MS) * DAY_MS;
                if (dayBoundaryNow > dayBoundary) {
                    s.open24h = s.price;
                    s.high24h = s.price;
                    s.low24h = s.price;
                    s.volume24h = BigDecimal.ZERO;
                } else {
                    if (s.price.compareTo(s.high24h) > 0) s.high24h = s.price;
                    if (s.price.compareTo(s.low24h) < 0) s.low24h = s.price;
                }

                double stepVolume = 0;
                BigDecimal move = s.price.subtract(oldPrice).divide(oldPrice, 8, RoundingMode.HALF_UP).abs();
                if (move.compareTo(BIG_MOVE_THRESHOLD) >= 0) {
                    int n = 1 + ThreadLocalRandom.current().nextInt(2);
                    double volScale = "HOT".equals(s.activity) ? 4.0 : "ACTIVE".equals(s.activity) ? 2.0 : 1.0;
                    for (int i = 0; i < n; i++) {
                        double amt = move.doubleValue() * 50 * volScale + 5;
                        addTrade(s, amt, now - i * 10);
                        stepVolume += amt;
                    }
                } else {
                    double small = 0.01 + ThreadLocalRandom.current().nextDouble() * 0.2;
                    if ("HOT".equals(s.activity)) small *= 2;
                    addTrade(s, small, now);
                    stepVolume += small;
                }

                s.volume24h = s.volume24h.add(BigDecimal.valueOf(stepVolume));
                updateBars(s, now, stepVolume);
                s.lastStepMs = now;
            }
        }
    }

    /** WebSocket 虚拟盘价格推送：每 200ms 广播一次，降低推送频率（价格仍每 100ms 步进） */
    @Scheduled(fixedRate = 200)
    public void broadcastVirtualPrices() {
        if (messagingTemplate == null || stateBySymbol.isEmpty()) return;
        try {
            messagingTemplate.convertAndSend("/topic/virtual-prices", snapshotForBroadcast());
        } catch (Exception e) {
            broadcastFailures.increment();
            log.warn("VirtualMarketEngine broadcastVirtualPrices failed: {}", e.toString());
        }
    }

    private void addTrade(VirtualState s, double amount, long time) {
        boolean buy = ThreadLocalRandom.current().nextBoolean();
        Map<String, Object> t = new HashMap<>();
        t.put("price", s.price);
        t.put("amount", BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP));
        t.put("direction", buy ? "BUY" : "SELL");
        t.put("time", time);
        s.recentTrades.addFirst(t);
        while (s.recentTrades.size() > MAX_TRADES) s.recentTrades.removeLast();
    }

    private void updateBars(VirtualState s, long now, double stepVolume) {
        for (Map.Entry<String, BarAggregator> entry : s.barsByInterval.entrySet()) {
            String interval = entry.getKey();
            BarAggregator agg = entry.getValue();
            long barMs = barMs(interval);
            long barStart = (now / barMs) * barMs;
            if (agg.currentBarTime < 0) {
                agg.currentBarTime = barStart;
                agg.currentOpen = s.price;
                agg.currentHigh = s.price;
                agg.currentLow = s.price;
                agg.currentClose = s.price;
                agg.currentVolume = BigDecimal.ZERO;
            }
            agg.currentHigh = agg.currentHigh.max(s.price);
            agg.currentLow = agg.currentLow.min(s.price);
            agg.currentClose = s.price;
            agg.currentVolume = agg.currentVolume.add(BigDecimal.valueOf(stepVolume));
            if (barStart > agg.currentBarTime) {
                Map<String, Object> completed = new HashMap<>();
                completed.put("time", agg.currentBarTime);
                completed.put("open", agg.currentOpen);
                completed.put("high", agg.currentHigh);
                completed.put("low", agg.currentLow);
                completed.put("close", agg.currentClose);
                completed.put("volume", agg.currentVolume);
                agg.completedBars.add(completed);
                int cap = Math.min(klineHistoryBars, MAX_COMPLETED_BARS_CAP);
                while (agg.completedBars.size() > cap) agg.completedBars.remove(0);
                agg.currentBarTime = barStart;
                agg.currentOpen = s.price;
                agg.currentHigh = s.price;
                agg.currentLow = s.price;
                agg.currentClose = s.price;
                agg.currentVolume = BigDecimal.ZERO;
            }
        }
    }

    private static long barMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            default -> 3600_000L; // 1h
        };
    }

    private void initBarAggregators(VirtualState s, String symbol) {
        s.barsByInterval.put("1m", new BarAggregator());
        s.barsByInterval.put("5m", new BarAggregator());
        s.barsByInterval.put("15m", new BarAggregator());
        s.barsByInterval.put("1h", new BarAggregator());
        fillBarHistory(s, symbol);
    }

    private static final double BAR_CHANGE_PCT = 0.05;
    private static final int RAMP_BARS = 100;

    /** 为虚拟盘预填历史 K 线：在配置价格区间内随机游走，最近 RAMP_BARS 根缓慢爬升到当前价。 */
    private void fillBarHistory(VirtualState s, String symbol) {
        long now = System.currentTimeMillis();
        java.util.Random r = ThreadLocalRandom.current();
        boolean useRange = "ZRX/USDT".equalsIgnoreCase(symbol) || (defaultPriceLow < defaultPriceHigh);
        double low = defaultPriceLow;
        double high = defaultPriceHigh;
        double targetPrice = s.price.doubleValue();
        if (useRange) targetPrice = Math.max(low, Math.min(high, targetPrice));
        synchronized (s) {
            for (Map.Entry<String, BarAggregator> entry : s.barsByInterval.entrySet()) {
                String interval = entry.getKey();
                BarAggregator agg = entry.getValue();
                long bMs = barMs(interval);
                long currentBarStart = (now / bMs) * bMs;
                double priceVal = s.price.doubleValue();
                if (useRange) priceVal = Math.max(low, Math.min(high, priceVal));
                BigDecimal price = BigDecimal.valueOf(priceVal).setScale(8, RoundingMode.HALF_UP);
                double rampStartPrice = priceVal;
                for (int i = 0; i < klineHistoryBars; i++) {
                    long barTime = currentBarStart - (long) (klineHistoryBars - i) * bMs;
                    BigDecimal open;
                    BigDecimal close;
                    if (useRange) {
                        open = price;
                        int rampFrom = klineHistoryBars - RAMP_BARS;
                        if (i >= rampFrom) {
                            if (i == rampFrom) rampStartPrice = price.doubleValue();
                            double progress = (double) (i - rampFrom + 1) / RAMP_BARS;
                            progress = Math.min(1.0, progress);
                            double closeVal = rampStartPrice + (targetPrice - rampStartPrice) * progress;
                            closeVal += (r.nextDouble() * 2 - 1) * 0.3;
                            closeVal = Math.max(low, Math.min(high, closeVal));
                            close = BigDecimal.valueOf(closeVal).setScale(8, RoundingMode.HALF_UP);
                        } else {
                            double change = (r.nextDouble() * 2 - 1) * BAR_CHANGE_PCT;
                            double closeVal = open.doubleValue() * (1 + change);
                            closeVal = Math.max(low, Math.min(high, closeVal));
                            close = BigDecimal.valueOf(closeVal).setScale(8, RoundingMode.HALF_UP);
                        }
                    } else {
                        open = price;
                        double drift = s.driftDaily * (STEP_MS / (double) DAY_MS);
                        double vol = s.volatility * Math.sqrt(STEP_MS / (double) DAY_MS);
                        double z = r.nextGaussian();
                        double factor = 1.0 + drift + vol * z;
                        if (factor <= 0 || Double.isNaN(factor)) factor = 0.999;
                        close = open.multiply(BigDecimal.valueOf(factor)).setScale(8, RoundingMode.HALF_UP);
                        if (close.compareTo(BigDecimal.ZERO) <= 0) close = open;
                    }
                    BigDecimal highPrice = open.max(close).multiply(BigDecimal.valueOf(1.0 + r.nextDouble() * 0.002)).setScale(8, RoundingMode.HALF_UP);
                    BigDecimal lowPrice = open.min(close).multiply(BigDecimal.valueOf(1.0 - r.nextDouble() * 0.002)).setScale(8, RoundingMode.HALF_UP);
                    if (useRange) {
                        highPrice = highPrice.min(BigDecimal.valueOf(high));
                        lowPrice = lowPrice.max(BigDecimal.valueOf(low));
                    } else if (lowPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        lowPrice = open.min(close);
                    }
                    BigDecimal volume = BigDecimal.valueOf(10 + r.nextDouble() * 200).setScale(2, RoundingMode.HALF_UP);
                    Map<String, Object> bar = new HashMap<>();
                    bar.put("time", barTime);
                    bar.put("open", open);
                    bar.put("high", highPrice);
                    bar.put("low", lowPrice);
                    bar.put("close", close);
                    bar.put("volume", volume);
                    agg.completedBars.add(bar);
                    price = close;
                }
            }
        }
    }

    /** 中心辐射型深度：以最新价为心，正态分布挂单量；tickSize 间距 */
    private void buildDepth(VirtualState s) {
        BigDecimal p = s.price;
        BigDecimal tick = s.tickSize;
        double volScale = "HOT".equals(s.activity) ? 6.0 : "ACTIVE".equals(s.activity) ? 2.5 : 1.0;
        List<Map<String, Object>> asks = new ArrayList<>();
        List<Map<String, Object>> bids = new ArrayList<>();
        for (int i = 1; i <= MAX_DEPTH; i++) {
            double dist = i * tick.doubleValue() / p.doubleValue();
            double density = Math.exp(-dist * dist * 20) * volScale;
            double amt = (50 + density * 400 + ThreadLocalRandom.current().nextDouble() * 100);
            BigDecimal askPrice = p.add(tick.multiply(BigDecimal.valueOf(i))).setScale(8, RoundingMode.HALF_UP);
            Map<String, Object> a = new HashMap<>();
            a.put("price", askPrice);
            a.put("amount", BigDecimal.valueOf(amt).setScale(2, RoundingMode.HALF_UP));
            asks.add(a);
            BigDecimal bidPrice = p.subtract(tick.multiply(BigDecimal.valueOf(i))).setScale(8, RoundingMode.HALF_UP);
            if (bidPrice.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> b = new HashMap<>();
                b.put("price", bidPrice);
                b.put("amount", BigDecimal.valueOf(amt).setScale(2, RoundingMode.HALF_UP));
                bids.add(b);
            }
        }
        s.asks = asks;
        s.bids = bids;
    }

    /** 每周一 00:00 对波动率做 ±20% 随机扰动，降低长期统计可预测性 */
    @Scheduled(cron = "0 0 0 ? * MON")
    public void weeklyVolatilityJitter() {
        if (!dynamicVolatilityEnabled) return;
        Random r = ThreadLocalRandom.current();
        for (VirtualState s : stateBySymbol.values()) {
            synchronized (s) {
                double jitter = 0.8 + r.nextDouble() * 0.4;
                s.volatilityMultiplier = Math.max(0.5, Math.min(2.0, s.volatilityMultiplier * jitter));
            }
        }
    }

    /** 每 4 小时在 GBM 与 OU 过程之间切换，重置随机性 */
    @Scheduled(fixedRate = 4 * 3600 * 1000L)
    public void toggleOuMode() {
        if (!ouModeEnabled) return;
        for (VirtualState s : stateBySymbol.values()) {
            synchronized (s) {
                s.useOu = !s.useOu;
            }
        }
    }

    /** 深度呼吸：随机撤几笔、补几笔 */
    @Scheduled(fixedRate = 300)
    public void breathDepth() {
        for (VirtualState s : stateBySymbol.values()) {
            synchronized (s) {
                if (s.asks.isEmpty()) continue;
                Random r = ThreadLocalRandom.current();
                if (r.nextDouble() < BREATH_PROB && s.asks.size() > 5) {
                    int idx = r.nextInt(Math.min(5, s.asks.size()));
                    s.asks.remove(idx);
                    BigDecimal p = s.price.add(s.tickSize.multiply(BigDecimal.valueOf(idx + 1))).setScale(8, RoundingMode.HALF_UP);
                    double amt = 30 + r.nextDouble() * 200;
                    Map<String, Object> a = new HashMap<>();
                    a.put("price", p);
                    a.put("amount", BigDecimal.valueOf(amt).setScale(2, RoundingMode.HALF_UP));
                    s.asks.add(Math.min(idx, s.asks.size()), a);
                }
                if (r.nextDouble() < BREATH_PROB && s.bids.size() > 5) {
                    int idx = r.nextInt(Math.min(5, s.bids.size()));
                    s.bids.remove(idx);
                    BigDecimal p = s.price.subtract(s.tickSize.multiply(BigDecimal.valueOf(idx + 1))).setScale(8, RoundingMode.HALF_UP);
                    if (p.compareTo(BigDecimal.ZERO) > 0) {
                        double amt = 30 + r.nextDouble() * 200;
                        Map<String, Object> b = new HashMap<>();
                        b.put("price", p);
                        b.put("amount", BigDecimal.valueOf(amt).setScale(2, RoundingMode.HALF_UP));
                        s.bids.add(Math.min(idx, s.bids.size()), b);
                    }
                }
            }
        }
    }

    public boolean isVirtual(String symbol) {
        return stateBySymbol.containsKey(symbol);
    }

    public Map<String, Object> getThumb(String symbol, String baseSymbol, String coinSymbol) {
        VirtualState s = stateBySymbol.get(symbol);
        if (s == null) return null;
        synchronized (s) {
            Map<String, Object> m = new HashMap<>();
            m.put("symbol", symbol);
            m.put("baseSymbol", baseSymbol);
            m.put("coinSymbol", coinSymbol);
            m.put("open", s.open24h);
            m.put("close", s.price);
            m.put("high", s.high24h);
            m.put("low", s.low24h);
            m.put("volume", s.volume24h.setScale(2, RoundingMode.HALF_UP));
            m.put("turnover", s.volume24h.multiply(s.price).setScale(2, RoundingMode.HALF_UP));
            if (s.open24h.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal chg = s.price.subtract(s.open24h).divide(s.open24h, 4, RoundingMode.HALF_UP);
                m.put("chg", chg);
                m.put("change", s.price.subtract(s.open24h).setScale(8, RoundingMode.HALF_UP));
            } else {
                m.put("chg", BigDecimal.ZERO);
                m.put("change", BigDecimal.ZERO);
            }
            m.put("virtual", true);
            m.put("virtualActivity", s.activity);
            return m;
        }
    }

    public Map<String, Object> getPlate(String symbol, int limit) {
        VirtualState s = stateBySymbol.get(symbol);
        if (s == null) return null;
        synchronized (s) {
            int n = Math.min(limit, s.asks.size());
            List<Map<String, Object>> askItems = new ArrayList<>(s.asks.subList(0, n));
            n = Math.min(limit, s.bids.size());
            List<Map<String, Object>> bidItems = new ArrayList<>(s.bids.subList(0, n));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ask", askItems);
            out.put("bid", bidItems);
            return out;
        }
    }

    public List<Map<String, Object>> getKline(String symbol, String interval, int limit, Long endTime) {
        VirtualState s = stateBySymbol.get(symbol);
        if (s == null) return null;
        synchronized (s) {
            BarAggregator agg = s.barsByInterval.get(interval);
            if (agg == null) agg = s.barsByInterval.get("1h");
            List<Map<String, Object>> list = new ArrayList<>(agg.completedBars);
            if (agg.currentBarTime >= 0) {
                Map<String, Object> cur = new HashMap<>();
                cur.put("time", agg.currentBarTime);
                cur.put("open", agg.currentOpen);
                cur.put("high", agg.currentHigh);
                cur.put("low", agg.currentLow);
                cur.put("close", agg.currentClose);
                cur.put("volume", agg.currentVolume);
                list.add(cur);
            }
            if (list.size() > limit) list = list.subList(list.size() - limit, list.size());
            return list;
        }
    }

    public List<Map<String, Object>> getLatestTrades(String symbol, int limit) {
        VirtualState s = stateBySymbol.get(symbol);
        if (s == null) return null;
        synchronized (s) {
            int n = Math.min(limit, s.recentTrades.size());
            List<Map<String, Object>> out = new ArrayList<>();
            Iterator<Map<String, Object>> it = s.recentTrades.iterator();
            for (int i = 0; i < n && it.hasNext(); i++) out.add(new HashMap<>(it.next()));
            return out;
        }
    }

    public BigDecimal getCurrentPrice(String symbol) {
        VirtualState s = stateBySymbol.get(symbol);
        if (s == null) return null;
        synchronized (s) {
            return s.price;
        }
    }

    /** 供 WebSocket 推送：返回所有虚拟 symbol 的当前价、时间、盘口深度与最近成交，前端订阅后无需轮询 plate/trades */
    public List<Map<String, Object>> snapshotForBroadcast() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, VirtualState> e : stateBySymbol.entrySet()) {
            VirtualState s = e.getValue();
            synchronized (s) {
                Map<String, Object> m = new HashMap<>();
                m.put("symbol", e.getKey());
                m.put("price", s.price);
                m.put("time", now);
                Map<String, Object> plate = getPlate(e.getKey(), MAX_DEPTH);
                if (plate != null) m.put("plate", plate);
                m.put("trades", new ArrayList<>(s.recentTrades));
                list.add(m);
            }
        }
        return list;
    }

    public void refreshCoins() {
        loadVirtualCoins();
    }

    private static class VirtualState {
        BigDecimal price;
        BigDecimal open24h, high24h, low24h, volume24h;
        long lastStepMs;
        double driftDaily, volatility;
        /** 每周 ±20% 随机扰动，降低长期可预测性 */
        double volatilityMultiplier = 1.0;
        /** 是否使用 OU 过程（每 4 小时与 GBM 切换） */
        boolean useOu = false;
        BigDecimal tickSize;
        String activity;
        List<Map<String, Object>> asks = new ArrayList<>();
        List<Map<String, Object>> bids = new ArrayList<>();
        final Deque<Map<String, Object>> recentTrades = new LinkedList<>();
        final Map<String, BarAggregator> barsByInterval = new HashMap<>();
    }

    private static class BarAggregator {
        long currentBarTime = -1;
        BigDecimal currentOpen, currentHigh, currentLow, currentClose, currentVolume = BigDecimal.ZERO;
        final List<Map<String, Object>> completedBars = new ArrayList<>();
    }
}
