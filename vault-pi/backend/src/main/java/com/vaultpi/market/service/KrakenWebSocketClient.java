package com.vaultpi.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.scheduling.TaskScheduler;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kraken 公开 WebSocket v2 客户端：订阅 ticker、ohlc(1/5/15/30/60)、book(25)，
 * 消息写入 Redis；断线秒级自动重连。
 */
@Slf4j
public class KrakenWebSocketClient extends WebSocketClient {

    private static final String WS_URL = "wss://ws.kraken.com/v2";
    private static final int[] OHLC_INTERVALS = { 1, 5, 15, 30, 60 };
    private static final int BOOK_DEPTH = 25;

    /** Kraken WS 使用 BTC/USD；我们统一存 BTC/USDT */
    private static final List<String> WS_SYMBOLS = Arrays.asList(
        "BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD", "DOGE/USD", "ADA/USD",
        "AVAX/USD", "LINK/USD", "DOT/USD", "MATIC/USD", "LTC/USD", "BCH/USD",
        "UNI/USD", "ATOM/USD"
    );

    private static String wsSymbolToOurs(String wsSymbol) {
        if (wsSymbol == null) return null;
        if ("USD".equals(wsSymbol.split("/")[1])) return wsSymbol.replace("/USD", "/USDT");
        return wsSymbol;
    }

    private final KrakenMarketRedisService redisService;
    private final MarketLatencyRecorder latencyRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> reconnectFuture;
    private final TaskScheduler taskScheduler;
    private final long reconnectDelayMs;

    public KrakenWebSocketClient(URI serverUri, KrakenMarketRedisService redisService,
                                 TaskScheduler taskScheduler, long reconnectDelayMs,
                                 MarketLatencyRecorder latencyRecorder) {
        super(serverUri);
        this.redisService = redisService;
        this.taskScheduler = taskScheduler;
        this.reconnectDelayMs = reconnectDelayMs;
        this.latencyRecorder = latencyRecorder;
    }

    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("Kraken WebSocket connected");
        connected.set(true);
        sendSubscribes();
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isBlank()) return;
        long t0 = System.nanoTime();
        try {
            Map<String, Object> root = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            String channel = (String) root.get("channel");
            if (channel == null) return;
            switch (channel) {
                case "ticker" -> handleTicker(root);
                case "ohlc" -> handleOhlc(root);
                case "book" -> handleBook(root);
                default -> {}
            }
            if (latencyRecorder != null) latencyRecorder.recordKrakenToRedis(System.nanoTime() - t0);
        } catch (Exception e) {
            log.trace("Kraken WS parse error: {}", e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("Kraken WebSocket closed: code={} reason={}", code, reason);
        connected.set(false);
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        log.error("Kraken WebSocket error: {}", ex.getMessage());
    }

    private void sendSubscribes() {
        try {
            // ticker
            Map<String, Object> tickerSub = new HashMap<>();
            tickerSub.put("method", "subscribe");
            tickerSub.put("params", Map.of("channel", "ticker", "symbol", WS_SYMBOLS));
            send(objectMapper.writeValueAsString(tickerSub));

            // ohlc per interval
            for (int interval : OHLC_INTERVALS) {
                Map<String, Object> ohlcSub = new HashMap<>();
                ohlcSub.put("method", "subscribe");
                ohlcSub.put("params", Map.of("channel", "ohlc", "symbol", WS_SYMBOLS, "interval", interval));
                send(objectMapper.writeValueAsString(ohlcSub));
            }

            // book
            Map<String, Object> bookSub = new HashMap<>();
            bookSub.put("method", "subscribe");
            bookSub.put("params", Map.of("channel", "book", "symbol", WS_SYMBOLS, "depth", BOOK_DEPTH));
            send(objectMapper.writeValueAsString(bookSub));
        } catch (Exception e) {
            log.error("Kraken WS send subscribe failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleTicker(Map<String, Object> root) {
        List<Object> dataList = (List<Object>) root.get("data");
        if (dataList == null || dataList.isEmpty()) return;
        Object first = dataList.get(0);
        if (!(first instanceof Map)) return;
        Map<String, Object> t = (Map<String, Object>) first;
        String symbol = (String) t.get("symbol");
        String ourSymbol = wsSymbolToOurs(symbol);
        if (ourSymbol == null) return;

        double last = num(t.get("last"));
        double high = num(t.get("high"));
        double low = num(t.get("low"));
        double volume = num(t.get("volume"));
        double change = num(t.get("change"));
        double changePct = num(t.get("change_pct"));
        double open = last - change;
        if (open <= 0) open = last;
        BigDecimal openBd = BigDecimal.valueOf(open);
        BigDecimal closeBd = BigDecimal.valueOf(last);
        BigDecimal highBd = BigDecimal.valueOf(high);
        BigDecimal lowBd = BigDecimal.valueOf(low);
        BigDecimal volumeBd = BigDecimal.valueOf(volume);
        BigDecimal changeBd = BigDecimal.valueOf(change);
        BigDecimal chgBd = openBd.compareTo(BigDecimal.ZERO) != 0
            ? changeBd.divide(openBd, 4, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal turnoverBd = volumeBd.multiply(closeBd);

        Map<String, Object> thumb = new HashMap<>();
        thumb.put("open", openBd);
        thumb.put("close", closeBd);
        thumb.put("high", highBd);
        thumb.put("low", lowBd);
        thumb.put("volume", volumeBd);
        thumb.put("turnover", turnoverBd);
        thumb.put("change", changeBd);
        thumb.put("chg", chgBd);
        redisService.putThumb(ourSymbol, thumb);
    }

    private static double num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleOhlc(Map<String, Object> root) {
        List<Object> dataList = (List<Object>) root.get("data");
        if (dataList == null) return;
        for (Object item : dataList) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> c = (Map<String, Object>) item;
            String symbol = (String) c.get("symbol");
            String ourSymbol = wsSymbolToOurs(symbol);
            Object intervalObj = c.get("interval");
            int interval = intervalObj instanceof Number ? ((Number) intervalObj).intValue() : 60;
            Map<String, Object> bar = new HashMap<>();
            bar.put("open", num(c.get("open")));
            bar.put("high", num(c.get("high")));
            bar.put("low", num(c.get("low")));
            bar.put("close", num(c.get("close")));
            bar.put("volume", num(c.get("volume")));
            Object begin = c.get("interval_begin");
            if (begin != null) {
                try {
                    if (begin instanceof String) bar.put("time", parseRfc3339ToMillis((String) begin));
                    else if (begin instanceof Number) bar.put("time", ((Number) begin).longValue() * 1000);
                } catch (Exception ignored) {}
            }
            redisService.putOhlcLast(ourSymbol, interval, bar);
        }
    }

    private static long parseRfc3339ToMillis(String s) {
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBook(Map<String, Object> root) {
        List<Object> dataList = (List<Object>) root.get("data");
        if (dataList == null || dataList.isEmpty()) return;
        Object first = dataList.get(0);
        if (!(first instanceof Map)) return;
        Map<String, Object> book = (Map<String, Object>) first;
        String symbol = (String) book.get("symbol");
        String ourSymbol = wsSymbolToOurs(symbol);
        if (ourSymbol == null) return;

        List<Map<String, Object>> askList = new ArrayList<>();
        List<List<Object>> asksRaw = (List<List<Object>>) book.get("asks");
        if (asksRaw != null) {
            for (List<Object> row : asksRaw) {
                if (row.size() >= 2) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("price", row.get(0));
                    m.put("amount", row.get(1));
                    askList.add(m);
                }
            }
        }
        List<Map<String, Object>> bidList = new ArrayList<>();
        List<List<Object>> bidsRaw = (List<List<Object>>) book.get("bids");
        if (bidsRaw != null) {
            for (List<Object> row : bidsRaw) {
                if (row.size() >= 2) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("price", row.get(0));
                    m.put("amount", row.get(1));
                    bidList.add(m);
                }
            }
        }
        redisService.putPlate(ourSymbol, askList, bidList);
    }

    private void scheduleReconnect() {
        if (reconnectFuture != null) return;
        reconnectFuture = taskScheduler.schedule(() -> {
            reconnectFuture = null;
            if (!isOpen()) {
                log.info("Kraken WebSocket reconnecting...");
                reconnect();
            }
        }, new Date(System.currentTimeMillis() + reconnectDelayMs));
    }

    @PreDestroy
    public void destroy() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
        close();
        connected.set(false);
    }
}
