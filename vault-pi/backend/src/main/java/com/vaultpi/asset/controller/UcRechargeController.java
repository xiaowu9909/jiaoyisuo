package com.vaultpi.asset.controller;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberDeposit;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberDepositRepository;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc/recharge", ApiPaths.V1 + "/uc/recharge" })
public class UcRechargeController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final CoinRepository coinRepository;
    private final MemberDepositRepository depositRepository;

    public UcRechargeController(CoinRepository coinRepository, MemberDepositRepository depositRepository) {
        this.coinRepository = coinRepository;
        this.depositRepository = depositRepository;
    }

    /** 已配置充币地址的币种列表（C 端充值页下拉仅展示此类） */
    @GetMapping("/coins")
    public Result<List<Map<String, String>>> coinsWithAddress(HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        List<Map<String, String>> list = coinRepository.findAll().stream()
            .filter(c -> c.getDepositAddress() != null && !c.getDepositAddress().isEmpty())
            .map(c -> Map.<String, String>of(
                "unit", c.getUnit(),
                "name", c.getName() != null && !c.getName().isEmpty() ? c.getName() : c.getUnit()
            ))
            .collect(Collectors.toList());
        return Result.ok(list);
    }

    @GetMapping("/address")
    public Result<Map<String, Object>> address(HttpSession session, @RequestParam String unit) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        var coin = coinRepository.findByUnit(unit);
        if (coin.isEmpty()) return Result.fail(404, "币种不存在");
        Coin c = coin.get();
        Map<String, Object> m = new HashMap<>();
        m.put("unit", c.getUnit());
        m.put("address", c.getDepositAddress() != null && !c.getDepositAddress().isEmpty() ? c.getDepositAddress() : "（请等待平台配置充币地址）");
        return Result.ok(m);
    }

    @GetMapping("/record")
    public Result<Map<String, Object>> record(HttpSession session,
                                              @RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        var page = depositRepository.findByMemberIdOrderByCreateTimeDesc(memberId, PageRequest.of(Math.max(0, pageNo - 1), Math.min(50, pageSize)));
        List<Map<String, Object>> content = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    /** 提交充值申请：填写金额并上传转账详情图，等待人工审核 */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(HttpSession session, @RequestBody Map<String, Object> body) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        String unit = body.get("unit") != null ? body.get("unit").toString().trim() : null;
        BigDecimal amount = null;
        if (body.get("amount") != null) {
            try {
                amount = new BigDecimal(body.get("amount").toString());
            } catch (Exception ignored) {}
        }
        String transferImage = body.get("transferImage") != null ? body.get("transferImage").toString() : null;
        if (unit == null || unit.isEmpty()) return Result.fail(400, "请选择币种");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Result.fail(400, "请填写有效充值金额");
        if (transferImage == null || transferImage.isEmpty()) return Result.fail(400, "请上传转账详情图");
        var coinOpt = coinRepository.findByUnit(unit);
        if (coinOpt.isEmpty()) return Result.fail(404, "币种不存在");
        Coin coin = coinOpt.get();
        MemberDeposit deposit = new MemberDeposit();
        deposit.setMemberId(memberId);
        deposit.setCoinId(coin.getId());
        deposit.setAmount(amount);
        deposit.setStatus("PENDING");
        deposit.setTransferImage(transferImage.length() > 50000 ? transferImage.substring(0, 50000) : transferImage);
        deposit = depositRepository.save(deposit);
        Map<String, Object> m = new HashMap<>();
        m.put("id", deposit.getId());
        m.put("status", deposit.getStatus());
        return Result.ok(m);
    }

    private Map<String, Object> toMap(MemberDeposit d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("coinId", d.getCoinId());
        m.put("amount", d.getAmount());
        m.put("address", d.getAddress() != null ? d.getAddress() : "");
        m.put("txId", d.getTxId() != null ? d.getTxId() : "");
        m.put("status", d.getStatus() != null ? d.getStatus() : "PENDING");
        m.put("createTime", d.getCreateTime() != null ? d.getCreateTime().toString() : "");
        m.put("hasTransferImage", d.getTransferImage() != null && !d.getTransferImage().isEmpty());
        m.put("rejectReason", d.getRejectReason() != null ? d.getRejectReason() : "");
        return m;
    }
}
