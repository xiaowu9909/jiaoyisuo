package com.vaultpi.asset.controller;

import com.vaultpi.asset.entity.MemberTransaction;
import com.vaultpi.asset.repository.MemberTransactionRepository;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc", ApiPaths.V1 + "/uc" })
public class UcTransactionController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final MemberTransactionRepository transactionRepository;

    public UcTransactionController(MemberTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/transaction/page")
    public Result<Map<String, Object>> page(HttpSession session,
                                            @RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        var page = transactionRepository.findByMemberIdOrderByCreateTimeDesc(memberId, PageRequest.of(Math.max(0, pageNo - 1), Math.min(50, pageSize)));
        List<Map<String, Object>> content = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    private Map<String, Object> toMap(MemberTransaction t) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("amount", t.getAmount());
        map.put("type", t.getType() != null ? t.getType() : "");
        map.put("symbol", t.getSymbol() != null ? t.getSymbol() : "");
        map.put("address", t.getAddress() != null ? t.getAddress() : "");
        map.put("fee", t.getFee() != null ? t.getFee() : java.math.BigDecimal.ZERO);
        map.put("createTime", t.getCreateTime() != null ? t.getCreateTime().toString() : "");
        return map;
    }
}
