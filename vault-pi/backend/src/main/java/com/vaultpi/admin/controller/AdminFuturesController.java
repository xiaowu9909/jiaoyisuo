package com.vaultpi.admin.controller;

import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.service.WalletService;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.futures.entity.ContractOrder;
import com.vaultpi.futures.entity.ContractPosition;
import com.vaultpi.futures.math.FuturesFormula;
import com.vaultpi.futures.repository.ContractOrderRepository;
import com.vaultpi.futures.repository.ContractPositionRepository;
import com.vaultpi.market.service.KrakenApiClient;
import com.vaultpi.market.service.VirtualMarketEngine;
import com.vaultpi.user.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/futures", ApiPaths.V1 + "/admin/futures" })
public class AdminFuturesController {

    private final ContractOrderRepository orderRepo;
    private final ContractPositionRepository positionRepo;
    private final MemberRepository memberRepo;
    private final KrakenApiClient krakenApiClient;
    private final CoinRepository coinRepo;
    private final MemberWalletRepository walletRepo;
    private final WalletService walletService;

    @Autowired(required = false)
    private VirtualMarketEngine virtualMarketEngine;

    public AdminFuturesController(ContractOrderRepository orderRepo,
                                  ContractPositionRepository positionRepo,
                                  MemberRepository memberRepo,
                                  KrakenApiClient krakenApiClient,
                                  CoinRepository coinRepo,
                                  MemberWalletRepository walletRepo,
                                  WalletService walletService) {
        this.orderRepo = orderRepo;
        this.positionRepo = positionRepo;
        this.memberRepo = memberRepo;
        this.krakenApiClient = krakenApiClient;
        this.coinRepo = coinRepo;
        this.walletRepo = walletRepo;
        this.walletService = walletService;
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
            BigDecimal markPrice = pos.getAvgPrice();
            BigDecimal pnlForDisplay;
            if ("OPEN".equals(pos.getStatus())) {
                BigDecimal mark = krakenApiClient.fetchCurrentPrice(pos.getSymbol());
                if (mark != null && mark.compareTo(BigDecimal.ZERO) > 0) {
                    markPrice = mark;
                }
                pnlForDisplay = FuturesFormula.unrealizedPnl(pos.getDirection(), markPrice, pos.getAvgPrice(), pos.getVolume());
            } else {
                pnlForDisplay = pos.getRealizedPnl() != null ? pos.getRealizedPnl() : BigDecimal.ZERO;
            }
            map.put("markPrice", markPrice);
            map.put("liquidationPrice", BigDecimal.ZERO);
            map.put("margin", pos.getMargin());
            map.put("unrealizedPnl", pnlForDisplay);
            map.put("roePercent", FuturesFormula.roePercent(pnlForDisplay, pos.getMargin()));
            map.put("realizedPnl", pos.getRealizedPnl() != null ? pos.getRealizedPnl() : BigDecimal.ZERO);
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

    @PostMapping("/position/close")
    @Transactional
    public Result<String> forceClosePosition(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("positionId");
        if (idObj == null) return Result.fail(400, "缺少 positionId");
        Long positionId;
        try {
            positionId = Long.parseLong(idObj.toString());
        } catch (NumberFormatException e) {
            return Result.fail(400, "positionId 格式错误");
        }

        ContractPosition pos = positionRepo.findById(positionId).orElse(null);
        if (pos == null) return Result.fail(404, "持仓不存在");
        if (!"OPEN".equals(pos.getStatus())) return Result.fail(400, "该持仓并非开启状态");

        BigDecimal currentPrice = BigDecimal.ZERO;
        if (virtualMarketEngine != null && virtualMarketEngine.isVirtual(pos.getSymbol())) {
            BigDecimal vp = virtualMarketEngine.getCurrentPrice(pos.getSymbol());
            if (vp != null && vp.compareTo(BigDecimal.ZERO) > 0) currentPrice = vp;
        }
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            currentPrice = krakenApiClient.fetchCurrentPrice(pos.getSymbol());
        }
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            currentPrice = pos.getAvgPrice(); // fallback to entry price
        }

        BigDecimal pnl = FuturesFormula.unrealizedPnl(pos.getDirection(), currentPrice, pos.getAvgPrice(), pos.getVolume());
        BigDecimal lossCap = pos.getMargin().negate();
        BigDecimal effectivePnl = pnl.max(lossCap);

        String[] parts = pos.getSymbol() != null ? pos.getSymbol().split("/") : new String[0];
        String baseSymbol = parts.length >= 2 ? parts[1] : "USDT";
        coinRepo.findByUnit(baseSymbol).ifPresent(coin -> {
            MemberWallet wallet = walletService.getOrCreateWallet(pos.getMemberId(), coin.getId());
            wallet.setBalance(wallet.getBalance().add(effectivePnl));
            walletRepo.save(wallet);
        });

        pos.setStatus("CLOSED");
        pos.setRealizedPnl(effectivePnl);
        positionRepo.save(pos);
        return Result.ok("强制平仓成功, 结算 PNL: " + effectivePnl.setScale(4, RoundingMode.HALF_UP));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("totalMembers", memberRepo.count());
        s.put("totalOrders", orderRepo.count());
        s.put("openPositions", positionRepo.findByStatus("OPEN").size());
        long pending = orderRepo.findAll().stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        s.put("pendingOrders", pending);
        return Result.ok(s);
    }
}
