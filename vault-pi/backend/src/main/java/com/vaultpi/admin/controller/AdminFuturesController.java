package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.futures.entity.ContractOrder;
import com.vaultpi.futures.entity.ContractPosition;
import com.vaultpi.futures.repository.ContractOrderRepository;
import com.vaultpi.futures.repository.ContractPositionRepository;
import com.vaultpi.user.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/futures", ApiPaths.V1 + "/admin/futures" })
public class AdminFuturesController {

    private final ContractOrderRepository orderRepo;
    private final ContractPositionRepository positionRepo;
    private final MemberRepository memberRepo;

    public AdminFuturesController(ContractOrderRepository orderRepo,
                                  ContractPositionRepository positionRepo,
                                  MemberRepository memberRepo) {
        this.orderRepo = orderRepo;
        this.positionRepo = positionRepo;
        this.memberRepo = memberRepo;
    }

    @GetMapping("/orders")
    public Result<Map<String, Object>> listOrders(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String symbol) {
        org.springframework.data.domain.Pageable pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, pageSize),
                Sort.by(Sort.Direction.DESC, "createTime"));
        
        org.springframework.data.domain.Page<ContractOrder> page;
        if (memberId != null && symbol != null && !symbol.isEmpty()) {
            page = orderRepo.findByMemberIdAndSymbolOrderByCreateTimeDesc(memberId, symbol, pageable);
        } else if (memberId != null) {
            page = orderRepo.findByMemberIdOrderByCreateTimeDesc(memberId, pageable);
        } else if (symbol != null && !symbol.isEmpty()) {
            page = orderRepo.findBySymbolOrderByCreateTimeDesc(symbol, pageable);
        } else {
            page = orderRepo.findAll(pageable);
        }

        List<Map<String, Object>> content = page.getContent().stream().map(order -> {
            var m = memberRepo.findById(order.getMemberId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", order.getOrderId());
            map.put("memberId", order.getMemberId());
            map.put("username", m != null ? m.getUsername() : "?");
            map.put("symbol", order.getSymbol());
            map.put("direction", order.getDirection());
            map.put("entrustPrice", order.getPrice());
            map.put("amount", order.getAmount());
            map.put("tradedAmount", order.getAmount());
            map.put("status", order.getStatus());
            map.put("type", order.getType());
            map.put("leverage", order.getLeverage());
            map.put("createTime", order.getCreateTime() != null ? order.getCreateTime().toString() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/positions")
    public Result<Map<String, Object>> listPositions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String symbol) {
        org.springframework.data.domain.Pageable pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, pageSize),
                Sort.by(Sort.Direction.DESC, "createTime"));
        
        org.springframework.data.domain.Page<ContractPosition> page;
        if (memberId != null && symbol != null && !symbol.isEmpty()) {
            page = positionRepo.findByMemberIdAndSymbolOrderByCreateTimeDesc(memberId, symbol, pageable);
        } else if (memberId != null) {
            page = positionRepo.findByMemberIdOrderByCreateTimeDesc(memberId, pageable);
        } else if (symbol != null && !symbol.isEmpty()) {
            page = positionRepo.findBySymbolOrderByCreateTimeDesc(symbol, pageable);
        } else {
            page = positionRepo.findAll(pageable);
        }

        List<Map<String, Object>> content = page.getContent().stream().map(pos -> {
            var m = memberRepo.findById(pos.getMemberId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", pos.getId());
            map.put("memberId", pos.getMemberId());
            map.put("username", m != null ? m.getUsername() : "?");
            map.put("symbol", pos.getSymbol());
            map.put("direction", pos.getDirection());
            map.put("leverage", pos.getLeverage());
            map.put("amount", pos.getVolume());
            map.put("entryPrice", pos.getAvgPrice());
            map.put("markPrice", pos.getAvgPrice());
            map.put("liquidationPrice", java.math.BigDecimal.ZERO);
            map.put("margin", pos.getMargin());
            map.put("unrealizedPnl", pos.getRealizedPnl());
            map.put("status", pos.getStatus());
            map.put("createTime", pos.getCreateTime() != null ? pos.getCreateTime().toString() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("totalMembers", memberRepo.count());
        s.put("totalOrders", orderRepo.count());
        s.put("openPositions", positionRepo.findByStatus("OPEN").size());
        long tradingOrders = orderRepo.findAll().stream().filter(o -> "TRADING".equals(o.getStatus())).count();
        s.put("pendingOrders", tradingOrders);
        return Result.ok(s);
    }
}
