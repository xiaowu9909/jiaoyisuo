package com.vaultpi.admin.controller;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.entity.MemberTransaction;
import com.vaultpi.asset.entity.WithdrawAddress;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberTransactionRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.repository.WithdrawAddressRepository;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.entity.VipLevelConfig;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.repository.VipLevelConfigRepository;
import com.vaultpi.user.service.MemberService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
 
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/member", ApiPaths.V1 + "/admin/member" })
public class AdminMemberController {
 
    private final MemberRepository memberRepository;
    private final MemberWalletRepository walletRepository;
    private final CoinRepository coinRepository;
    private final MemberTransactionRepository transactionRepository;
    private final WithdrawAddressRepository withdrawAddressRepository;
    private final MemberService memberService;
    private final VipLevelConfigRepository vipLevelConfigRepository;

    public AdminMemberController(MemberRepository memberRepository,
                                 MemberWalletRepository walletRepository,
                                 CoinRepository coinRepository,
                                 MemberTransactionRepository transactionRepository,
                                 WithdrawAddressRepository withdrawAddressRepository,
                                 MemberService memberService,
                                 VipLevelConfigRepository vipLevelConfigRepository) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.coinRepository = coinRepository;
        this.transactionRepository = transactionRepository;
        this.withdrawAddressRepository = withdrawAddressRepository;
        this.memberService = memberService;
        this.vipLevelConfigRepository = vipLevelConfigRepository;
    }

    /** 会员等级配置列表（VIP0-VIP6），若表为空则初始化默认并返回 */
    @GetMapping("/vip-level/list")
    public Result<List<Map<String, Object>>> vipLevelList() {
        List<VipLevelConfig> list = vipLevelConfigRepository.findAllByOrderByLevelAsc();
        if (list.isEmpty()) {
            for (int level = 0; level <= 6; level++) {
                VipLevelConfig c = new VipLevelConfig();
                c.setLevel(level);
                c.setRechargeThreshold(level == 0 ? BigDecimal.ZERO : new BigDecimal(new String[]{"0", "1000", "5000", "20000", "50000", "100000", "200000"}[level]));
                c.setLeverageMultiplier(new int[] { 5, 10, 20, 50, 75, 100, 125 }[level]);
                vipLevelConfigRepository.save(c);
                list.add(c);
            }
        }
        List<Map<String, Object>> out = list.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("level", c.getLevel());
            m.put("rechargeThreshold", c.getRechargeThreshold());
            m.put("leverageMultiplier", c.getLeverageMultiplier());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(out);
    }

    /** 批量更新会员等级配置 */
    @PostMapping("/vip-level/update")
    public Result<String> vipLevelUpdate(@RequestBody List<Map<String, Object>> body) {
        if (body == null || body.isEmpty()) return Result.fail(400, "请提交至少一条配置");
        for (Map<String, Object> item : body) {
            Integer level = item.get("level") != null ? Integer.valueOf(item.get("level").toString()) : null;
            if (level == null || level < 0 || level > 6) continue;
            VipLevelConfig c = vipLevelConfigRepository.findByLevel(level).orElse(new VipLevelConfig());
            c.setLevel(level);
            if (item.get("rechargeThreshold") != null) c.setRechargeThreshold(new BigDecimal(item.get("rechargeThreshold").toString()));
            if (item.get("leverageMultiplier") != null) c.setLeverageMultiplier(Integer.valueOf(item.get("leverageMultiplier").toString()));
            vipLevelConfigRepository.save(c);
        }
        return Result.ok("保存成功");
    }
 
    /** 更新会员：用户类型（内部/正常）、上级（传 parentId 或 parentInviteCode，传空则清除上级） */
    @PostMapping("/update-base")
    public Result<String> updateBase(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        if (id == null) return Result.fail(400, "缺少会员 ID");
        Member m = memberRepository.findById(id).orElse(null);
        if (m == null) return Result.fail(404, "会员不存在");

        String userType = body.get("userType") != null ? body.get("userType").toString().trim() : null;
        if (userType != null && !userType.isEmpty()) {
            if ("INTERNAL".equalsIgnoreCase(userType)) m.setUserType("INTERNAL");
            else if ("NORMAL".equalsIgnoreCase(userType)) m.setUserType("NORMAL");
            else return Result.fail(400, "userType 只能为 NORMAL 或 INTERNAL");
        }

        Object parentIdObj = body.get("parentId");
        String parentInviteCode = body.get("parentInviteCode") != null ? body.get("parentInviteCode").toString().trim() : null;
        if (parentInviteCode != null && !parentInviteCode.isEmpty()) {
            Member parent = memberRepository.findByInviteCode(parentInviteCode.toUpperCase()).orElse(null);
            if (parent == null) return Result.fail(400, "未找到该邀请码对应的会员");
            if (parent.getId().equals(id)) return Result.fail(400, "不能将自己设为上级");
            m.setParentId(parent.getId());
        } else if (body.containsKey("parentId")) {
            if (parentIdObj == null || "".equals(parentIdObj.toString().trim())) {
                m.setParentId(null);
            } else {
                Long parentId = Long.valueOf(parentIdObj.toString());
                if (parentId.equals(id)) return Result.fail(400, "不能将自己设为上级");
                if (!memberRepository.existsById(parentId)) return Result.fail(400, "上级会员不存在");
                m.setParentId(parentId);
            }
        }

        Object vipLevelObj = body.get("vipLevel");
        if (vipLevelObj != null) {
            int lv = vipLevelObj instanceof Number ? ((Number) vipLevelObj).intValue() : Integer.parseInt(vipLevelObj.toString());
            if (lv >= 0 && lv <= 6) m.setVipLevel(lv);
        }
        if (body.containsKey("totalRecharge") && body.get("totalRecharge") != null) {
            m.setTotalRecharge(new BigDecimal(body.get("totalRecharge").toString()));
        }

        memberRepository.save(m);
        return Result.ok("更新成功");
    }

    @PostMapping("/status")
    public Result<String> updateStatus(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        String status = body.get("status") != null ? body.get("status").toString() : null;
        if (id == null || status == null) return Result.fail(400, "参数错误");
        
        Member m = memberRepository.findById(id).orElse(null);
        if (m == null) return Result.fail(404, "会员不存在");
        
        m.setStatus(status);
        memberRepository.save(m);
        return Result.ok("状态更新成功");
    }
 
    @PostMapping("/password/reset")
    public Result<String> resetPassword(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        String password = body.get("password") != null ? body.get("password").toString() : null;
        if (id == null || password == null) return Result.fail(400, "参数错误");
        
        try {
            memberService.updatePassword(id, password);
            return Result.ok("密码重置成功");
        } catch (Exception e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PostMapping("/add")
    public Result<Map<String, Object>> addUser(@RequestBody Map<String, Object> body) {
        String username = body != null && body.get("username") != null ? body.get("username").toString().trim() : null;
        String password = body != null && body.get("password") != null ? body.get("password").toString() : null;
        String userType = body != null && body.get("userType") != null ? body.get("userType").toString() : "NORMAL";
        String referrerInviteCode = body != null && body.get("referrerInviteCode") != null ? body.get("referrerInviteCode").toString().trim() : null;
        if (username == null || username.isEmpty()) {
            return Result.fail(400, "用户名不能为空");
        }
        String pwdErr = password != null ? com.vaultpi.common.PasswordPolicy.validate(password) : "请填写密码";
        if (pwdErr != null) return Result.fail(400, pwdErr);
        try {
            Member m = memberService.createMemberByAdmin(username, password, userType, referrerInviteCode);
            Map<String, Object> data = new HashMap<>();
            data.put("id", m.getId());
            data.put("uid", m.getUid());
            data.put("username", m.getUsername());
            data.put("userType", m.getUserType());
            data.put("inviteCode", m.getInviteCode());
            data.put("parentId", m.getParentId());
            return Result.ok(data);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long id) {
        Member m = memberRepository.findById(id).orElse(null);
        if (m == null) return Result.fail(404, "会员不存在");
        memberService.ensureUid(m);
        memberService.ensureInternalInviteCode(m);
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", m.getId());
        detail.put("uid", m.getUid());
        detail.put("userType", m.getUserType() != null ? m.getUserType() : "NORMAL");
        detail.put("username", m.getUsername());
        detail.put("email", m.getEmail());
        detail.put("phone", m.getPhone());
        detail.put("nickname", m.getNickname());
        detail.put("realName", m.getRealName());
        detail.put("idCard", m.getIdCard());
        detail.put("inviteCode", m.getInviteCode());
        detail.put("parentId", m.getParentId());
        if (m.getParentId() != null) {
            memberRepository.findById(m.getParentId()).ifPresent(p -> {
                detail.put("parentUid", p.getUid());
                detail.put("parentUsername", p.getUsername());
            });
        }
        detail.put("status", m.getStatus());
        detail.put("role", m.getRole());
        detail.put("vipLevel", m.getVipLevel() != null ? m.getVipLevel() : 0);
        detail.put("totalRecharge", m.getTotalRecharge() != null ? m.getTotalRecharge() : BigDecimal.ZERO);
        detail.put("registrationTime", m.getRegistrationTime() != null ? m.getRegistrationTime().toString() : null);
        detail.put("lastLoginTime", m.getLastLoginTime() != null ? m.getLastLoginTime().toString() : null);

        List<MemberWallet> wallets = walletRepository.findByMemberId(id);
        List<Map<String, Object>> walletList = wallets.stream().map(w -> {
            String unit = coinRepository.findById(w.getCoinId()).map(Coin::getUnit).orElse("?");
            return Map.<String, Object>of(
                "id", w.getId(),
                "coinId", w.getCoinId(),
                "unit", unit,
                "balance", w.getBalance(),
                "frozenBalance", w.getFrozenBalance()
            );
        }).collect(Collectors.toList());
        detail.put("wallets", walletList);

        List<WithdrawAddress> addrs = withdrawAddressRepository.findByMemberIdOrderByCoinId(id);
        List<Map<String, Object>> addrList = addrs.stream().map(a -> {
            String unit = coinRepository.findById(a.getCoinId()).map(Coin::getUnit).orElse("?");
            return Map.<String, Object>of(
                "id", a.getId(),
                "coinId", a.getCoinId(),
                "unit", unit,
                "address", a.getAddress() != null ? a.getAddress() : "",
                "remark", a.getRemark() != null ? a.getRemark() : ""
            );
        }).collect(Collectors.toList());
        detail.put("withdrawAddresses", addrList);
        detail.put("hasWithdrawPassword", memberService.hasWithdrawPassword(id));

        return Result.ok(detail);
    }

    /** 管理端：重置会员提现密码 */
    @PostMapping("/withdraw-password/reset")
    public Result<String> withdrawPasswordReset(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        String password = body.get("password") != null ? body.get("password").toString() : null;
        if (id == null || password == null) return Result.fail(400, "请填写会员 id 和新提现密码");
        String pwdErr = com.vaultpi.common.PasswordPolicy.validate(password);
        if (pwdErr != null) return Result.fail(400, "新提现密码：" + pwdErr);
        if (memberRepository.findById(id).orElse(null) == null) {
            return Result.fail(404, "会员不存在");
        }
        memberService.setWithdrawPassword(id, password);
        return Result.ok("提现密码已重置");
    }

    /** 获取可用币种列表（用于送彩金等选择） */
    @GetMapping("/coins")
    public Result<List<Map<String, Object>>> listCoins() {
        List<Map<String, Object>> list = coinRepository.findByEnableTrue().stream()
            .map(c -> Map.<String, Object>of("id", c.getId(), "unit", c.getUnit()))
            .collect(Collectors.toList());
        return Result.ok(list);
    }

    /** 管理端：新增会员提现地址 */
    @PostMapping("/withdraw-address/add")
    public Result<Map<String, Object>> withdrawAddressAdd(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        Long memberId = body.get("memberId") != null ? Long.valueOf(body.get("memberId").toString()) : null;
        Long coinId = body.get("coinId") != null ? Long.valueOf(body.get("coinId").toString()) : null;
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString().trim() : null;
        if (memberId == null || coinId == null || address == null || address.isEmpty()) {
            return Result.fail(400, "请填写会员、币种和地址");
        }
        if (memberRepository.findById(memberId).orElse(null) == null) {
            return Result.fail(404, "会员不存在");
        }
        WithdrawAddress a = new WithdrawAddress();
        a.setMemberId(memberId);
        a.setCoinId(coinId);
        a.setAddress(address);
        a.setRemark(remark != null && !remark.isEmpty() ? remark : null);
        a = withdrawAddressRepository.save(a);
        String unit = coinRepository.findById(coinId).map(Coin::getUnit).orElse("?");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("coinId", a.getCoinId());
        data.put("unit", unit);
        data.put("address", a.getAddress());
        data.put("remark", a.getRemark() != null ? a.getRemark() : "");
        return Result.ok(data);
    }

    /** 管理端：更新会员提现地址 */
    @PostMapping("/withdraw-address/update")
    public Result<String> withdrawAddressUpdate(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        Long memberId = body.get("memberId") != null ? Long.valueOf(body.get("memberId").toString()) : null;
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString().trim() : null;
        if (id == null || memberId == null) return Result.fail(400, "缺少 id 或 memberId");
        WithdrawAddress a = withdrawAddressRepository.findById(id).orElse(null);
        if (a == null || !a.getMemberId().equals(memberId)) {
            return Result.fail(404, "提现地址不存在或无权修改");
        }
        if (address != null && !address.isEmpty()) a.setAddress(address);
        a.setRemark(remark != null && !remark.isEmpty() ? remark : null);
        withdrawAddressRepository.save(a);
        return Result.ok("已更新");
    }

    /** 管理端：删除会员提现地址 */
    @PostMapping("/withdraw-address/delete")
    public Result<String> withdrawAddressDelete(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        Long memberId = body.get("memberId") != null ? Long.valueOf(body.get("memberId").toString()) : null;
        if (id == null || memberId == null) return Result.fail(400, "缺少 id 或 memberId");
        withdrawAddressRepository.findById(id).filter(a -> a.getMemberId().equals(memberId)).ifPresent(withdrawAddressRepository::delete);
        return Result.ok("已删除");
    }

    /** 送彩金：固定 USDT，给用户赠送余额，记 BONUS 流水，不参与充值统计。无 USDT 时自动创建，保证必成 */
    @PostMapping("/bonus")
    public Result<String> sendBonus(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        Long memberId = body.get("memberId") != null ? Long.valueOf(body.get("memberId").toString()) : null;
        String amountStr = body.get("amount") != null ? body.get("amount").toString() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : "送彩金";

        if (memberId == null || amountStr == null || amountStr.isEmpty()) {
            return Result.fail(400, "请填写会员和金额");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail(400, "金额必须大于 0");
            }
        } catch (Exception e) {
            return Result.fail(400, "金额格式错误");
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return Result.fail(404, "会员不存在");
        }
        Coin coin = coinRepository.findByUnit("USDT").orElse(null);
        if (coin == null) {
            coin = new Coin();
            coin.setUnit("USDT");
            coin.setName("USDT");
            coin.setEnable(true);
            coin = coinRepository.save(coin);
        }
        long coinId = coin.getId();
        MemberWallet wallet = walletRepository.findByMemberIdAndCoinId(memberId, coinId).orElse(null);
        if (wallet == null) {
            wallet = new MemberWallet();
            wallet.setMemberId(memberId);
            wallet.setCoinId(coinId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenBalance(BigDecimal.ZERO);
        }
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setSymbol(coin.getUnit());
        tx.setAmount(amount);
        tx.setType("BONUS");
        tx.setFee(BigDecimal.ZERO);
        tx.setCreateTime(java.time.Instant.now());
        transactionRepository.save(tx);

        return Result.ok("送彩金成功");
    }

    @PostMapping("/balance/update")
    public Result<String> updateBalance(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        
        Long memberId = body.get("memberId") != null ? Long.valueOf(body.get("memberId").toString()) : null;
        Long coinId = body.get("coinId") != null ? Long.valueOf(body.get("coinId").toString()) : null;
        Integer type = body.get("type") != null ? Integer.valueOf(body.get("type").toString()) : null; // 0=可用, 1=冻结
        Integer action = body.get("action") != null ? Integer.valueOf(body.get("action").toString()) : null; // 0=增加, 1=扣除
        String amountStr = body.get("amount") != null ? body.get("amount").toString() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : "";

        if (memberId == null || coinId == null || type == null || action == null || amountStr == null || amountStr.isEmpty()) {
            return Result.fail(400, "参数不完整");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail(400, "金额必须大于0");
            }
        } catch (Exception e) {
            return Result.fail(400, "金额格式错误");
        }

        MemberWallet wallet = walletRepository.findByMemberIdAndCoinId(memberId, coinId).orElse(null);
        if (wallet == null) {
            return Result.fail(400, "钱包不存在");
        }

        String unit = coinRepository.findById(coinId).map(Coin::getUnit).orElse("?");

        // Apply change
        if (action == 0) { // Add
            if (type == 0) {
                wallet.setBalance(wallet.getBalance().add(amount));
            } else {
                wallet.setFrozenBalance(wallet.getFrozenBalance().add(amount));
            }
        } else { // Deduct
            if (type == 0) {
                if (wallet.getBalance().compareTo(amount) < 0) {
                    return Result.fail(400, "可用余额不足");
                }
                wallet.setBalance(wallet.getBalance().subtract(amount));
            } else {
                if (wallet.getFrozenBalance().compareTo(amount) < 0) {
                    return Result.fail(400, "冻结余额不足");
                }
                wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
            }
        }

        walletRepository.save(wallet);

        // 多币种流水留痕：记录调账流水，扣款用负金额 + ADMIN_DEDUCT
        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setSymbol(unit);
        BigDecimal recordAmount = action == 0 ? amount : amount.negate();
        tx.setAmount(recordAmount);
        tx.setType(action == 0 ? "ADMIN_RECHARGE" : "ADMIN_DEDUCT");
        tx.setFee(BigDecimal.ZERO);
        tx.setCreateTime(java.time.Instant.now());
        transactionRepository.save(tx);

        return Result.ok("余额修改成功");
    }

    /** 解除冻结：将指定币种的冻结余额全部或部分退回可用余额 */
    @PostMapping("/balance/unfreeze")
    public Result<String> unfreezeBalance(@RequestBody Map<String, Object> body) {
        if (body == null) return Result.fail(400, "参数为空");
        Long memberId = body.get("memberId") != null ? Long.valueOf(body.get("memberId").toString()) : null;
        Long coinId = body.get("coinId") != null ? Long.valueOf(body.get("coinId").toString()) : null;
        String amountStr = body.get("amount") != null ? body.get("amount").toString().trim() : null;
        if (memberId == null || coinId == null) {
            return Result.fail(400, "缺少 memberId 或 coinId");
        }
        MemberWallet wallet = walletRepository.findByMemberIdAndCoinId(memberId, coinId).orElse(null);
        if (wallet == null) {
            return Result.fail(400, "钱包不存在");
        }
        BigDecimal toUnfreeze;
        if (amountStr == null || amountStr.isEmpty()) {
            toUnfreeze = wallet.getFrozenBalance();
        } else {
            try {
                toUnfreeze = new BigDecimal(amountStr);
                if (toUnfreeze.compareTo(BigDecimal.ZERO) <= 0) {
                    return Result.fail(400, "解除金额须大于 0");
                }
                if (wallet.getFrozenBalance().compareTo(toUnfreeze) < 0) {
                    return Result.fail(400, "冻结余额不足");
                }
            } catch (Exception e) {
                return Result.fail(400, "金额格式错误");
            }
        }
        if (toUnfreeze.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.ok("该币种无冻结余额，无需解除");
        }
        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(toUnfreeze));
        wallet.setBalance(wallet.getBalance().add(toUnfreeze));
        walletRepository.save(wallet);
        String unit = coinRepository.findById(coinId).map(Coin::getUnit).orElse("?");
        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setSymbol(unit);
        tx.setAmount(toUnfreeze);
        tx.setType("UNFREEZE");
        tx.setFee(BigDecimal.ZERO);
        tx.setCreateTime(java.time.Instant.now());
        transactionRepository.save(tx);
        return Result.ok("已解除冻结，资金已退回可用余额");
    }
}
