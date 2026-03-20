package com.vaultpi.asset.controller;

import com.vaultpi.asset.entity.*;
import com.vaultpi.asset.repository.*;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import com.vaultpi.user.service.MemberService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc/withdraw", ApiPaths.V1 + "/uc/withdraw" })
public class UcWithdrawController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final WithdrawAddressRepository addressRepository;
    private final WithdrawRecordRepository recordRepository;
    private final MemberWalletRepository walletRepository;
    private final CoinRepository coinRepository;
    private final MemberTransactionRepository transactionRepository;
    private final MemberService memberService;

    public UcWithdrawController(WithdrawAddressRepository addressRepository,
                               WithdrawRecordRepository recordRepository,
                               MemberWalletRepository walletRepository,
                               CoinRepository coinRepository,
                               MemberTransactionRepository transactionRepository,
                               MemberService memberService) {
        this.addressRepository = addressRepository;
        this.recordRepository = recordRepository;
        this.walletRepository = walletRepository;
        this.coinRepository = coinRepository;
        this.transactionRepository = transactionRepository;
        this.memberService = memberService;
    }

    @GetMapping("/address/list")
    public Result<List<Map<String, Object>>> addressList(HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        var list = addressRepository.findByMemberIdOrderByCoinId(memberId);
        var coinIds = list.stream().map(WithdrawAddress::getCoinId).distinct().toList();
        var coinMap = coinIds.stream().map(coinRepository::findById).filter(java.util.Optional::isPresent).map(java.util.Optional::get).collect(Collectors.toMap(Coin::getId, c -> c));
        List<Map<String, Object>> out = list.stream().map(a -> {
            Coin c = coinMap.get(a.getCoinId());
            return Map.<String, Object>of(
                "id", a.getId(),
                "coinId", a.getCoinId(),
                "unit", c != null ? c.getUnit() : "?",
                "address", a.getAddress(),
                "remark", a.getRemark() != null ? a.getRemark() : ""
            );
        }).collect(Collectors.toList());
        return Result.ok(out);
    }

    @PostMapping("/address/add")
    public Result<WithdrawAddress> addressAdd(@RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        Long coinId = parseLongSafe(body.get("coinId"));
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        if (coinId == null || address == null || address.isEmpty()) return Result.fail(400, "请选择币种并填写地址");
        WithdrawAddress a = new WithdrawAddress();
        a.setMemberId(memberId);
        a.setCoinId(coinId);
        a.setAddress(address);
        a.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        a = addressRepository.save(a);
        return Result.ok(a);
    }

    @PostMapping("/address/delete")
    public Result<String> addressDelete(@RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        Long id = parseLongSafe(body.get("id"));
        if (id == null) return Result.fail(400, "缺少或无效的 id");
        addressRepository.findById(id).filter(a -> a.getMemberId().equals(memberId)).ifPresent(addressRepository::delete);
        return Result.ok("已删除");
    }

    @PostMapping("/address/update")
    public Result<String> addressUpdate(@RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        Long id = parseLongSafe(body.get("id"));
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString().trim() : null;
        if (id == null) return Result.fail(400, "缺少 id");
        WithdrawAddress a = addressRepository.findById(id).filter(addr -> addr.getMemberId().equals(memberId)).orElse(null);
        if (a == null) return Result.fail(404, "提现地址不存在或无权修改");
        if (address != null && !address.isEmpty()) a.setAddress(address);
        a.setRemark(remark != null && !remark.isEmpty() ? remark : null);
        addressRepository.save(a);
        return Result.ok("已更新");
    }

    @PostMapping
    @Transactional
    public Result<String> apply(@RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        if (!memberService.isRealNameVerified(memberId)) {
            return Result.fail(403, "请先完成实名认证后再提现");
        }
        if (!memberService.hasWithdrawPassword(memberId)) {
            return Result.fail(403, "请先在安全设置中设置提现密码后再提现");
        }
        String withdrawPwd = body.get("withdrawPassword") != null ? body.get("withdrawPassword").toString() : null;
        if (withdrawPwd == null || withdrawPwd.isEmpty()) {
            return Result.fail(400, "请填写提现密码");
        }
        if (!memberService.checkWithdrawPassword(memberId, withdrawPwd)) {
            return Result.fail(400, "提现密码错误");
        }
        Long coinId = parseLongSafe(body.get("coinId"));
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        BigDecimal amount = parseDecimalSafe(body.get("amount"));
        if (coinId == null || address == null || address.isEmpty() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail(400, "请填写币种、地址和数量");
        }
        var walletOpt = walletRepository.findByMemberIdAndCoinId(memberId, coinId);
        if (walletOpt.isEmpty()) return Result.fail(400, "钱包不存在");
        MemberWallet wallet = walletOpt.get();
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal arrived = amount.subtract(fee);
        if (wallet.getBalance().compareTo(amount) < 0) return Result.fail(400, "余额不足");
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
        WithdrawRecord r = new WithdrawRecord();
        r.setMemberId(memberId);
        r.setCoinId(coinId);
        r.setTotalAmount(amount);
        r.setFee(fee);
        r.setArrivedAmount(arrived);
        r.setAddress(address);
        r.setStatus("PENDING");
        recordRepository.save(r);
        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setAmount(amount.negate());
        tx.setType("WITHDRAW");
        tx.setSymbol(coinRepository.findById(coinId).map(Coin::getUnit).orElse(""));
        tx.setAddress(address);
        tx.setFee(fee);
        transactionRepository.save(tx);
        return Result.ok("提现申请已提交");
    }

    @GetMapping("/record")
    public Result<Map<String, Object>> record(HttpSession session,
                                              @RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        var page = recordRepository.findByMemberIdOrderByCreateTimeDesc(memberId, PageRequest.of(Math.max(0, pageNo - 1), Math.min(50, pageSize)));
        var coinIds = page.getContent().stream().map(WithdrawRecord::getCoinId).distinct().toList();
        var coinMap = coinIds.stream().map(coinRepository::findById).filter(java.util.Optional::isPresent).map(java.util.Optional::get).collect(Collectors.toMap(Coin::getId, c -> c));
        List<Map<String, Object>> content = page.getContent().stream().map(r -> {
            Coin c = coinMap.get(r.getCoinId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("coinId", r.getCoinId());
            m.put("unit", c != null ? c.getUnit() : "?");
            m.put("totalAmount", r.getTotalAmount());
            m.put("fee", r.getFee());
            m.put("arrivedAmount", r.getArrivedAmount());
            m.put("address", r.getAddress() != null ? r.getAddress() : "");
            m.put("status", r.getStatus());
            m.put("createTime", r.getCreateTime() != null ? r.getCreateTime().toString() : "");
            m.put("remark", r.getRemark() != null ? r.getRemark() : "");
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    private static Long parseLongSafe(Object v) {
        if (v == null) return null;
        try {
            return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimalSafe(Object v) {
        if (v == null) return null;
        try {
            return v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
