package com.vaultpi.ai.service;

import com.vaultpi.ai.entity.AiPlan;
import com.vaultpi.ai.entity.AiStrategyPhrase;
import com.vaultpi.ai.entity.AiSubscribeRecord;
import com.vaultpi.ai.repository.AiPlanRepository;
import com.vaultpi.ai.repository.AiStrategyPhraseRepository;
import com.vaultpi.ai.repository.AiSubscribeRecordRepository;
import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.service.WalletService;
import com.vaultpi.exchange.entity.ExchangeOrder;
import com.vaultpi.exchange.repository.ExchangeOrderRepository;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AIService {

    private final AiPlanRepository aiPlanRepository;
    private final AiSubscribeRecordRepository aiSubscribeRecordRepository;
    private final MemberRepository memberRepository;
    private final CoinRepository coinRepository;
    private final MemberWalletRepository memberWalletRepository;
    private final WalletService walletService;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final AiStrategyPhraseRepository aiStrategyPhraseRepository;

    public AIService(AiPlanRepository aiPlanRepository,
                     AiSubscribeRecordRepository aiSubscribeRecordRepository,
                     MemberRepository memberRepository,
                     CoinRepository coinRepository,
                     MemberWalletRepository memberWalletRepository,
                     WalletService walletService,
                     ExchangeOrderRepository exchangeOrderRepository,
                     AiStrategyPhraseRepository aiStrategyPhraseRepository) {
        this.aiPlanRepository = aiPlanRepository;
        this.aiSubscribeRecordRepository = aiSubscribeRecordRepository;
        this.memberRepository = memberRepository;
        this.coinRepository = coinRepository;
        this.memberWalletRepository = memberWalletRepository;
        this.walletService = walletService;
        this.exchangeOrderRepository = exchangeOrderRepository;
        this.aiStrategyPhraseRepository = aiStrategyPhraseRepository;
    }

    /**
     * 购买AI订阅套餐
     */
    @Transactional
    public String purchaseSubscription(Long userId, Integer planId) {
        // 1. Check plan exists and is on-shelf
        AiPlan plan = aiPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("套餐不存在或已下架"));
        if (plan.getStatus() != 1) {
            throw new RuntimeException("套餐不存在或已下架");
        }

        // 2. Get USDT wallet
        Coin usdtCoin = coinRepository.findByUnit("USDT")
                .orElseThrow(() -> new RuntimeException("USDT币种不存在"));
        MemberWallet wallet = walletService.getOrCreateWallet(userId, usdtCoin.getId());

        // 3. Check balance
        if (wallet.getBalance().compareTo(plan.getPrice()) < 0) {
            throw new RuntimeException("余额不足，无法开启AI服务");
        }

        // 4. Deduct balance
        wallet.setBalance(wallet.getBalance().subtract(plan.getPrice()));
        memberWalletRepository.save(wallet);

        // 5. Update member AI subscription
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Instant now = Instant.now();
        Instant newExpireTime;
        if (member.getAiExpireTime() == null || member.getAiExpireTime().isBefore(now)) {
            newExpireTime = now.plus(plan.getDays(), ChronoUnit.DAYS);
        } else {
            newExpireTime = member.getAiExpireTime().plus(plan.getDays(), ChronoUnit.DAYS);
        }
        member.setAiExpireTime(newExpireTime);
        member.setAiStatus(1);
        memberRepository.save(member);

        // 6. Save subscribe record
        AiSubscribeRecord record = new AiSubscribeRecord();
        record.setUserId(userId);
        record.setPlanName(plan.getName());
        record.setCost(plan.getPrice());
        record.setCreatedAt(now);
        aiSubscribeRecordRepository.save(record);

        return "AI量化服务已开启，到期时间：" + newExpireTime.toString();
    }

    /**
     * 获取所有上架套餐
     */
    public List<AiPlan> getAvailablePlans() {
        return aiPlanRepository.findByStatus(1);
    }

    /**
     * 按类型获取已启用的话术
     */
    public List<AiStrategyPhrase> getAiPhrasesByType(int type) {
        return aiStrategyPhraseRepository.findByTypeAndStatus(type, 1);
    }

    /**
     * 获取用户的AI订单（is_ai=1）
     */
    public List<ExchangeOrder> getAiOrders(Long memberId) {
        return exchangeOrderRepository.findByMemberIdAndIsAiOrderByCreateTimeDesc(memberId, 1);
    }

    /**
     * 管理员取消用户AI订阅
     */
    @Transactional
    public void cancelAiForUser(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        member.setAiStatus(0);
        memberRepository.save(member);
    }
}
