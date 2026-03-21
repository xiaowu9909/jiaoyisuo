package com.vaultpi.ai.task;

import com.vaultpi.ai.entity.AiStrategyPhrase;
import com.vaultpi.ai.repository.AiStrategyPhraseRepository;
import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.service.WalletService;
import com.vaultpi.exchange.entity.ExchangeOrder;
import com.vaultpi.exchange.repository.ExchangeOrderRepository;
import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.market.service.VirtualMarketEngine;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class AISilentExecutionTask {

    private static final String[] SYMBOLS = {
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT", "BNB/USDT"
    };

    private final MemberRepository memberRepository;
    private final CoinRepository coinRepository;
    private final MemberWalletRepository memberWalletRepository;
    private final WalletService walletService;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final AiStrategyPhraseRepository aiStrategyPhraseRepository;
    private final KrakenApiClient krakenApiClient;

    @Autowired(required = false)
    private VirtualMarketEngine virtualMarketEngine;

    public AISilentExecutionTask(MemberRepository memberRepository,
                                 CoinRepository coinRepository,
                                 MemberWalletRepository memberWalletRepository,
                                 WalletService walletService,
                                 ExchangeOrderRepository exchangeOrderRepository,
                                 AiStrategyPhraseRepository aiStrategyPhraseRepository,
                                 KrakenApiClient krakenApiClient) {
        this.memberRepository = memberRepository;
        this.coinRepository = coinRepository;
        this.memberWalletRepository = memberWalletRepository;
        this.walletService = walletService;
        this.exchangeOrderRepository = exchangeOrderRepository;
        this.aiStrategyPhraseRepository = aiStrategyPhraseRepository;
        this.krakenApiClient = krakenApiClient;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void executeAiOrders() {
        // 1. Find all members with active AI subscription
        List<Member> activeMembers = memberRepository.findByAiStatusAndAiExpireTimeAfter(1, Instant.now());
        if (activeMembers.isEmpty()) return;

        Coin usdtCoin = coinRepository.findByUnit("USDT").orElse(null);
        if (usdtCoin == null) {
            log.warn("AISilentExecutionTask: USDT coin not found, skipping.");
            return;
        }

        for (Member member : activeMembers) {
            try {
                // 2. 5% chance to trigger per member
                if (Math.random() >= 0.05) continue;

                // Get USDT wallet balance
                MemberWallet wallet = walletService.getOrCreateWallet(member.getId(), usdtCoin.getId());
                BigDecimal currentBalance = wallet.getBalance();

                // 3. Random roll: profit / loss / missed
                double roll = Math.random();

                // 4. Pick random symbol
                String symbol = SYMBOLS[(int) (Math.random() * SYMBOLS.length)];

                // 5. Get current price
                BigDecimal currentPrice = getCurrentPrice(symbol);
                if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("AISilentExecutionTask: could not get price for {}, skipping.", symbol);
                    continue;
                }

                // 6. Simulate open price (15min ago price)
                double priceVariation = Math.random() * 0.03 - 0.015;
                BigDecimal openPrice = currentPrice.multiply(
                    BigDecimal.ONE.add(BigDecimal.valueOf(priceVariation))
                ).setScale(8, RoundingMode.HALF_UP);

                if (roll < 0.6) {
                    // Profit order
                    executeProfitOrder(member, wallet, symbol, currentPrice, openPrice, currentBalance, usdtCoin);
                } else if (roll < 0.8) {
                    // Loss order
                    executeLossOrder(member, wallet, symbol, currentPrice, openPrice, currentBalance, usdtCoin);
                } else {
                    // Missed order
                    executeMissedOrder(member, symbol, currentPrice, currentBalance);
                }

            } catch (Exception e) {
                log.warn("AISilentExecutionTask: error processing member {}: {}", member.getId(), e.getMessage());
            }
        }
    }

    private void executeProfitOrder(Member member, MemberWallet wallet, String symbol,
                                     BigDecimal currentPrice, BigDecimal openPrice,
                                     BigDecimal currentBalance, Coin usdtCoin) {
        double amount = currentBalance.doubleValue() * (0.1 + Math.random() * 0.2);
        BigDecimal tradeAmount = BigDecimal.valueOf(amount).setScale(8, RoundingMode.HALF_UP);

        double profitRate = 0.01 + Math.random() * 0.04;
        BigDecimal profitAmount = tradeAmount.multiply(BigDecimal.valueOf(profitRate)).setScale(8, RoundingMode.HALF_UP);

        // Update wallet balance
        wallet.setBalance(wallet.getBalance().add(profitAmount));
        memberWalletRepository.save(wallet);

        // Get random profit phrase
        String note = getRandomPhrase(2, "量化策略执行完毕，本次操作实现盈利");

        // Create AI order
        ExchangeOrder order = buildBaseOrder(member.getId(), symbol, "BUY", "AI", openPrice,
                tradeAmount, tradeAmount, "FILLED", note, profitAmount);
        exchangeOrderRepository.save(order);

        log.info("AISilentExecutionTask: member {} profit order created, symbol={}, profit={}",
                member.getId(), symbol, profitAmount);
    }

    private void executeLossOrder(Member member, MemberWallet wallet, String symbol,
                                   BigDecimal currentPrice, BigDecimal openPrice,
                                   BigDecimal currentBalance, Coin usdtCoin) {
        double amount = currentBalance.doubleValue() * (0.1 + Math.random() * 0.2);
        BigDecimal tradeAmount = BigDecimal.valueOf(amount).setScale(8, RoundingMode.HALF_UP);

        double lossRate = 0.01 + Math.random() * 0.03;
        BigDecimal lossAmount = tradeAmount.multiply(BigDecimal.valueOf(lossRate)).setScale(8, RoundingMode.HALF_UP).negate();

        // Update wallet balance (subtract loss)
        wallet.setBalance(wallet.getBalance().add(lossAmount));
        memberWalletRepository.save(wallet);

        // Get random loss phrase
        String note = getRandomPhrase(3, "策略执行完毕，本次操作产生计划内风险对冲亏损");

        // Create AI order
        ExchangeOrder order = buildBaseOrder(member.getId(), symbol, "SELL", "AI", openPrice,
                tradeAmount, tradeAmount, "FILLED", note, lossAmount);
        exchangeOrderRepository.save(order);

        log.warn("AISilentExecutionTask: member {} loss order created, symbol={}, loss={}",
                member.getId(), symbol, lossAmount);
    }

    private void executeMissedOrder(Member member, String symbol,
                                     BigDecimal currentPrice, BigDecimal currentBalance) {
        double fakeAmountRaw = currentBalance.doubleValue() * (0.15 + Math.random() * 0.15);
        BigDecimal fakeAmount = BigDecimal.valueOf(fakeAmountRaw).setScale(8, RoundingMode.HALF_UP);

        double fakeProfitRate = 0.02 + Math.random() * 0.06;
        BigDecimal fakeProfit = fakeAmount.multiply(BigDecimal.valueOf(fakeProfitRate)).setScale(8, RoundingMode.HALF_UP);

        double requiredBalanceMultiplier = 1.2 + Math.random() * 0.3;
        BigDecimal requiredBalance = currentBalance.multiply(BigDecimal.valueOf(requiredBalanceMultiplier))
                .setScale(8, RoundingMode.HALF_UP);

        // Get random missed phrase
        String note = getRandomPhrase(4, "账户余额不足，本次高胜率信号被迫跳过");

        // Create missed order (do NOT update wallet balance)
        ExchangeOrder order = new ExchangeOrder();
        order.setOrderId("AI" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        order.setMemberId(member.getId());
        order.setSymbol(symbol);
        order.setDirection("BUY");
        order.setType("AI_MISSED");
        order.setPrice(currentPrice);
        order.setAmount(fakeAmount);
        order.setTradedAmount(BigDecimal.ZERO);
        order.setStatus("MISSED");
        order.setIsAi(1);
        order.setIsMissed(1);
        order.setRequiredBalance(requiredBalance);
        order.setAiNote(note);
        order.setProfit(fakeProfit);
        exchangeOrderRepository.save(order);

        log.info("AISilentExecutionTask: member {} missed order created, symbol={}, requiredBalance={}",
                member.getId(), symbol, requiredBalance);
    }

    private ExchangeOrder buildBaseOrder(Long memberId, String symbol, String direction,
                                          String type, BigDecimal price, BigDecimal amount,
                                          BigDecimal tradedAmount, String status,
                                          String aiNote, BigDecimal profit) {
        ExchangeOrder order = new ExchangeOrder();
        order.setOrderId("AI" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        order.setMemberId(memberId);
        order.setSymbol(symbol);
        order.setDirection(direction);
        order.setType(type);
        order.setPrice(price);
        order.setAmount(amount);
        order.setTradedAmount(tradedAmount);
        order.setStatus(status);
        order.setIsAi(1);
        order.setIsMissed(0);
        order.setAiNote(aiNote);
        order.setProfit(profit);
        return order;
    }

    private BigDecimal getCurrentPrice(String symbol) {
        try {
            if (virtualMarketEngine != null && virtualMarketEngine.isVirtual(symbol)) {
                return virtualMarketEngine.getCurrentPrice(symbol);
            }
        } catch (Exception ignored) {
        }
        try {
            return krakenApiClient.fetchCurrentPrice(symbol);
        } catch (Exception e) {
            log.warn("AISilentExecutionTask: failed to get price for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String getRandomPhrase(int type, String fallback) {
        try {
            List<AiStrategyPhrase> phrases = aiStrategyPhraseRepository.findByTypeAndStatus(type, 1);
            if (!phrases.isEmpty()) {
                return phrases.get((int) (Math.random() * phrases.size())).getContent();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
