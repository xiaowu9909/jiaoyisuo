package com.vaultpi.admin.controller;

import com.vaultpi.ai.entity.AiPlan;
import com.vaultpi.ai.entity.AiStrategyPhrase;
import com.vaultpi.ai.repository.AiPlanRepository;
import com.vaultpi.ai.repository.AiStrategyPhraseRepository;
import com.vaultpi.ai.service.AIService;
import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.service.WalletService;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/ai", ApiPaths.V1 + "/admin/ai" })
public class AdminAIController {

    private final AiStrategyPhraseRepository aiStrategyPhraseRepository;
    private final AiPlanRepository aiPlanRepository;
    private final MemberRepository memberRepository;
    private final CoinRepository coinRepository;
    private final WalletService walletService;
    private final AIService aiService;

    public AdminAIController(AiStrategyPhraseRepository aiStrategyPhraseRepository,
                              AiPlanRepository aiPlanRepository,
                              MemberRepository memberRepository,
                              CoinRepository coinRepository,
                              WalletService walletService,
                              AIService aiService) {
        this.aiStrategyPhraseRepository = aiStrategyPhraseRepository;
        this.aiPlanRepository = aiPlanRepository;
        this.memberRepository = memberRepository;
        this.coinRepository = coinRepository;
        this.walletService = walletService;
        this.aiService = aiService;
    }

    // ===== Phrases =====

    @GetMapping("/phrases")
    public Result<List<Map<String, Object>>> listPhrases() {
        List<AiStrategyPhrase> all = aiStrategyPhraseRepository.findAll();
        List<Map<String, Object>> result = all.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("type", p.getType());
            m.put("content", p.getContent());
            m.put("status", p.getStatus());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @PostMapping("/phrases/add")
    public Result<Object> addPhrase(@RequestBody Map<String, Object> body) {
        AiStrategyPhrase phrase = new AiStrategyPhrase();
        phrase.setType(toInt(body.get("type")));
        phrase.setContent((String) body.get("content"));
        phrase.setStatus(body.containsKey("status") ? toInt(body.get("status")) : 1);
        aiStrategyPhraseRepository.save(phrase);
        return Result.ok("添加成功");
    }

    @PostMapping("/phrases/update")
    public Result<Object> updatePhrase(@RequestBody Map<String, Object> body) {
        Integer id = toInt(body.get("id"));
        AiStrategyPhrase phrase = aiStrategyPhraseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("话术不存在"));
        if (body.containsKey("type")) phrase.setType(toInt(body.get("type")));
        if (body.containsKey("content")) phrase.setContent((String) body.get("content"));
        if (body.containsKey("status")) phrase.setStatus(toInt(body.get("status")));
        aiStrategyPhraseRepository.save(phrase);
        return Result.ok("更新成功");
    }

    @PostMapping("/phrases/delete")
    public Result<Object> deletePhrase(@RequestBody Map<String, Object> body) {
        Integer id = toInt(body.get("id"));
        aiStrategyPhraseRepository.deleteById(id);
        return Result.ok("删除成功");
    }

    // ===== AI Users =====

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listAiUsers() {
        List<Member> members = memberRepository.findByAiStatus(1);
        Coin usdtCoin = coinRepository.findByUnit("USDT").orElse(null);

        List<Map<String, Object>> result = members.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("username", m.getUsername());
            map.put("aiExpireTime", m.getAiExpireTime());

            BigDecimal balance = BigDecimal.ZERO;
            if (usdtCoin != null) {
                try {
                    MemberWallet wallet = walletService.getOrCreateWallet(m.getId(), usdtCoin.getId());
                    balance = wallet.getBalance();
                } catch (Exception ignored) {
                }
            }
            map.put("balance", balance);
            return map;
        }).collect(Collectors.toList());

        return Result.ok(result);
    }

    @PostMapping("/users/cancel")
    public Result<Object> cancelAiUser(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        aiService.cancelAiForUser(userId);
        return Result.ok("已取消AI服务");
    }

    // ===== Plans =====

    @GetMapping("/plans")
    public Result<List<AiPlan>> listPlans() {
        return Result.ok(aiPlanRepository.findAll());
    }

    @PostMapping("/plans/add")
    public Result<Object> addPlan(@RequestBody Map<String, Object> body) {
        AiPlan plan = new AiPlan();
        plan.setName((String) body.get("name"));
        plan.setPrice(new BigDecimal(body.get("price").toString()));
        plan.setDays(toInt(body.get("days")));
        plan.setStatus(body.containsKey("status") ? toInt(body.get("status")) : 1);
        aiPlanRepository.save(plan);
        return Result.ok("添加成功");
    }

    @PostMapping("/plans/update")
    public Result<Object> updatePlan(@RequestBody Map<String, Object> body) {
        Integer id = toInt(body.get("id"));
        AiPlan plan = aiPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("套餐不存在"));
        if (body.containsKey("name")) plan.setName((String) body.get("name"));
        if (body.containsKey("price")) plan.setPrice(new BigDecimal(body.get("price").toString()));
        if (body.containsKey("days")) plan.setDays(toInt(body.get("days")));
        if (body.containsKey("status")) plan.setStatus(toInt(body.get("status")));
        aiPlanRepository.save(plan);
        return Result.ok("更新成功");
    }

    @PostMapping("/plans/delete")
    public Result<Object> deletePlan(@RequestBody Map<String, Object> body) {
        Integer id = toInt(body.get("id"));
        aiPlanRepository.deleteById(id);
        return Result.ok("删除成功");
    }

    // ===== Helpers =====

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        return Integer.parseInt(val.toString());
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        return Long.parseLong(val.toString());
    }
}
