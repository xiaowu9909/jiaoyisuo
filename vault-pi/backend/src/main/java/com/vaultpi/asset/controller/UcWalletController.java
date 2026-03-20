package com.vaultpi.asset.controller;
 
import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberTransactionRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.entity.MemberTransaction;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import com.vaultpi.common.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
 
import com.vaultpi.market.service.KrakenApiClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个人中心 - 资产（需登录）
 */
@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc", ApiPaths.V1 + "/uc" })
public class UcWalletController {

    private final MemberWalletRepository memberWalletRepository;
    private final CoinRepository coinRepository;
    private final MemberTransactionRepository transactionRepository;
    private final KrakenApiClient krakenApiClient;

    public UcWalletController(MemberWalletRepository memberWalletRepository,
                              CoinRepository coinRepository,
                              MemberTransactionRepository transactionRepository,
                              KrakenApiClient krakenApiClient) {
        this.memberWalletRepository = memberWalletRepository;
        this.coinRepository = coinRepository;
        this.transactionRepository = transactionRepository;
        this.krakenApiClient = krakenApiClient;
    }

    @GetMapping("/wallet/list")
    public Result<List<Map<String, Object>>> list(HttpSession session) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) {
            return Result.fail(401, "请先登录");
        }
        List<MemberWallet> wallets = memberWalletRepository.findByMemberId(memberId);
        List<Long> coinIds = wallets.stream().map(MemberWallet::getCoinId).distinct().collect(Collectors.toList());
        Map<Long, Coin> coinMap = new HashMap<>();
        for (Long id : coinIds) {
            Optional<Coin> c = coinRepository.findById(id);
            if (c.isPresent()) coinMap.put(id, c.get());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (MemberWallet w : wallets) {
            Coin c = coinMap.get(w.getCoinId());
            String unit = c != null ? c.getUnit() : "?";
            BigDecimal all = w.getBalance().add(w.getFrozenBalance());
            
            Map<String, Object> map = new HashMap<>();
            map.put("id", w.getId());
            map.put("coinId", w.getCoinId());
            map.put("unit", unit);
            map.put("balance", w.getBalance());
            map.put("frozenBalance", w.getFrozenBalance());
            map.put("allBalance", all);
            list.add(map);
        }
        return Result.ok(list);
    }

    @PostMapping("/asset/exchange")
    @Transactional
    public Result<String> exchange(HttpSession session, @RequestBody ExchangeRequest req) {
        Long memberId = SessionUtil.getMemberId(session);
        if (memberId == null) return Result.fail(401, "请先登录");

        if (req.getAmount().compareTo(BigDecimal.ZERO) <= 0) return Result.fail("金额必须大于0");
        if (req.getFromUnit().equals(req.getToUnit())) return Result.fail("相同币种无需兑换");

        Optional<Coin> fromCoinOpt = coinRepository.findByUnit(req.getFromUnit());
        Optional<Coin> toCoinOpt = coinRepository.findByUnit(req.getToUnit());
        if (fromCoinOpt.isEmpty() || toCoinOpt.isEmpty()) return Result.fail("币种不存在");

        Coin fromCoin = fromCoinOpt.get();
        Coin toCoin = toCoinOpt.get();

        Optional<MemberWallet> fromWalletOpt = memberWalletRepository.findByMemberIdAndCoinId(memberId, fromCoin.getId());
        if (!fromWalletOpt.isPresent() || fromWalletOpt.get().getBalance().compareTo(req.getAmount()) < 0) {
            return Result.fail("余额不足");
        }
        MemberWallet fromWallet = fromWalletOpt.get();

        Optional<MemberWallet> toWalletOpt = memberWalletRepository.findByMemberIdAndCoinId(memberId, toCoin.getId());
        MemberWallet toWallet;
        if (!toWalletOpt.isPresent()) {
            toWallet = new MemberWallet();
            toWallet.setMemberId(memberId);
            toWallet.setCoinId(toCoin.getId());
            toWallet.setBalance(BigDecimal.ZERO);
            toWallet.setFrozenBalance(BigDecimal.ZERO);
        } else {
            toWallet = toWalletOpt.get();
        }

        // Get prices from Kraken to calculate rate
        BigDecimal fromPrice = getPrice(fromCoin.getUnit());
        BigDecimal toPrice = getPrice(toCoin.getUnit());

        if (fromPrice.compareTo(BigDecimal.ZERO) <= 0 || toPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail("获取汇率失败");
        }

        BigDecimal rate = fromPrice.divide(toPrice, 8, RoundingMode.HALF_UP);
        BigDecimal targetAmount = req.getAmount().multiply(rate).setScale(8, RoundingMode.DOWN);

        // Update wallets
        fromWallet.setBalance(fromWallet.getBalance().subtract(req.getAmount()));
        toWallet.setBalance(toWallet.getBalance().add(targetAmount));

        memberWalletRepository.save(fromWallet);
        memberWalletRepository.save(toWallet);

        // Record transactions
        recordTransaction(memberId, req.getAmount().negate(), "EXCHANGE", fromCoin.getUnit());
        recordTransaction(memberId, targetAmount, "EXCHANGE", toCoin.getUnit());

        return Result.ok("兑换成功");
    }

    private BigDecimal getPrice(String unit) {
        if ("USDT".equalsIgnoreCase(unit)) return BigDecimal.ONE;
        return krakenApiClient.fetchCurrentPrice(unit + "/USDT");
    }

    private void recordTransaction(Long memberId, BigDecimal amount, String type, String symbol) {
        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setSymbol(symbol);
        transactionRepository.save(tx);
    }

    @Data
    public static class ExchangeRequest {
        private String fromUnit;
        private String toUnit;
        private BigDecimal amount;
    }
}
