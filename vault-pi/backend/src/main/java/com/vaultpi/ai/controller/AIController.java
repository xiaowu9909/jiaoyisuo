package com.vaultpi.ai.controller;

import com.vaultpi.ai.entity.AiPlan;
import com.vaultpi.ai.entity.AiStrategyPhrase;
import com.vaultpi.ai.service.AIService;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import com.vaultpi.common.SessionUtil;
import com.vaultpi.exchange.entity.ExchangeOrder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/ai", ApiPaths.V1 + "/ai" })
@RequireLogin
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * 获取AI话术（按类型）：type=1 运行状态流, type=2 盈利理由, type=3 亏损理由, type=4 错过理由
     */
    @GetMapping("/phrases")
    public Result<List<Map<String, Object>>> getPhrases(@RequestParam(defaultValue = "1") int type) {
        List<AiStrategyPhrase> phrases = aiService.getAiPhrasesByType(type);
        List<Map<String, Object>> result = phrases.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("type", p.getType());
            m.put("content", p.getContent());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    /**
     * 获取当前用户的AI订单列表
     */
    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> getAiOrders(HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        List<ExchangeOrder> orders = aiService.getAiOrders(memberId);
        List<Map<String, Object>> result = orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getOrderId());
            m.put("symbol", o.getSymbol());
            m.put("direction", o.getDirection());
            m.put("price", o.getPrice());
            m.put("amount", o.getAmount());
            m.put("tradedAmount", o.getTradedAmount());
            m.put("profit", o.getProfit());
            m.put("isAi", o.getIsAi());
            m.put("isMissed", o.getIsMissed());
            m.put("requiredBalance", o.getRequiredBalance());
            m.put("aiNote", o.getAiNote());
            m.put("status", o.getStatus());
            m.put("createTime", o.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    /**
     * 获取可购买的AI套餐列表（status=1）
     */
    @GetMapping("/plans")
    public Result<List<AiPlan>> getPlans() {
        return Result.ok(aiService.getAvailablePlans());
    }

    /**
     * 购买AI订阅套餐
     */
    @PostMapping("/subscribe/purchase")
    public Result<String> purchaseSubscription(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        Integer planId = toInt(body.get("planId"));
        String msg = aiService.purchaseSubscription(memberId, planId);
        return Result.ok(msg);
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        return Integer.parseInt(val.toString());
    }
}
