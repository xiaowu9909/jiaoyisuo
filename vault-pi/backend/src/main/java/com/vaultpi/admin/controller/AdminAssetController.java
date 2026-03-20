package com.vaultpi.admin.controller;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberTransaction;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.entity.WithdrawRecord;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberDepositRepository;
import com.vaultpi.asset.repository.MemberTransactionRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.asset.repository.WithdrawRecordRepository;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.service.MemberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/finance", ApiPaths.V1 + "/admin/finance" })
public class AdminAssetController {

    private final MemberRepository memberRepository;
    private final CoinRepository coinRepository;
    private final WithdrawRecordRepository withdrawRecordRepository;
    private final MemberDepositRepository memberDepositRepository;
    private final MemberWalletRepository memberWalletRepository;
    private final MemberTransactionRepository memberTransactionRepository;
    private final MemberService memberService;

    public AdminAssetController(MemberRepository memberRepository,
                                CoinRepository coinRepository,
                                WithdrawRecordRepository withdrawRecordRepository,
                                MemberDepositRepository memberDepositRepository,
                                MemberWalletRepository memberWalletRepository,
                                MemberTransactionRepository memberTransactionRepository,
                                MemberService memberService) {
        this.memberRepository = memberRepository;
        this.coinRepository = coinRepository;
        this.withdrawRecordRepository = withdrawRecordRepository;
        this.memberDepositRepository = memberDepositRepository;
        this.memberWalletRepository = memberWalletRepository;
        this.memberTransactionRepository = memberTransactionRepository;
        this.memberService = memberService;
    }

    @GetMapping("/withdraw/page")
    public Result<Map<String, Object>> withdrawPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        var sort = Sort.by(Sort.Direction.DESC, "createTime");
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(50, pageSize)), sort);
        
        var page = (status != null && !status.isEmpty()) 
                ? withdrawRecordRepository.findByStatusOrderByCreateTimeDesc(status, pageable)
                : withdrawRecordRepository.findAll(pageable);

        List<Map<String, Object>> content = page.getContent().stream().map(record -> {
            Member member = memberRepository.findById(record.getMemberId()).orElse(null);
            Coin coin = coinRepository.findById(record.getCoinId()).orElse(null);
            
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.getId());
            map.put("memberId", record.getMemberId());
            map.put("username", member != null ? member.getUsername() : "?");
            map.put("coinId", record.getCoinId());
            map.put("unit", coin != null ? coin.getUnit() : "?");
            map.put("totalAmount", record.getTotalAmount());
            map.put("fee", record.getFee());
            map.put("arrivedAmount", record.getArrivedAmount());
            map.put("address", record.getAddress());
            map.put("remark", record.getRemark() != null ? record.getRemark() : "");
            map.put("status", record.getStatus());
            map.put("createTime", record.getCreateTime() != null ? record.getCreateTime().toString() : "");
            map.put("dealTime", record.getDealTime() != null ? record.getDealTime().toString() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @PostMapping("/withdraw/audit")
    @Transactional
    public Result<String> withdrawAudit(@RequestBody Map<String, Object> body) {
        Long id = parseLongSafe(body.get("id"));
        String status = body.get("status") != null ? body.get("status").toString().trim() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString().trim() : null;

        if (id == null || status == null) {
            return Result.fail(400, "缺少参数");
        }

        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            return Result.fail(400, "状态只能是 APPROVED 或 REJECTED");
        }

        WithdrawRecord record = withdrawRecordRepository.findById(id).orElse(null);
        if (record == null) {
            return Result.fail(404, "提现记录不存在");
        }

        if (!"PENDING".equals(record.getStatus()) && !"PROCESSING".equals(record.getStatus())) {
            return Result.fail(400, "该申请已处理");
        }

        record.setStatus(status);
        record.setRemark(remark);
        record.setDealTime(Instant.now());

        // Refund exact deduction if request is denied
        if ("REJECTED".equals(status)) {
            MemberWallet wallet = memberWalletRepository.findByMemberIdAndCoinId(record.getMemberId(), record.getCoinId()).orElse(null);
            if (wallet != null) {
                // Assuming funds were already drawn when requested. Re-adding them completely.
                wallet.setBalance(wallet.getBalance().add(record.getTotalAmount()));
                memberWalletRepository.save(wallet);

                // Add Refund entry to Member Transaction Logger
                MemberTransaction tx = new MemberTransaction();
                tx.setMemberId(record.getMemberId());
                tx.setSymbol(coinRepository.findById(record.getCoinId()).map(Coin::getUnit).orElse("?"));
                tx.setAmount(record.getTotalAmount());
                tx.setType("WITHDRAW_REFUND");
                tx.setFee(BigDecimal.ZERO);
                tx.setCreateTime(Instant.now());
                memberTransactionRepository.save(tx);
            }
        }

        withdrawRecordRepository.save(record);
        return Result.ok("审核通过");
    }

    @GetMapping("/deposit/page")
    public Result<Map<String, Object>> depositPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long coinId) {
        var sort = Sort.by(Sort.Direction.DESC, "createTime");
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(50, pageSize)), sort);
        
        org.springframework.data.domain.Page<com.vaultpi.asset.entity.MemberDeposit> page;
        if (memberId != null && coinId != null) {
            page = memberDepositRepository.findByMemberIdAndCoinIdOrderByCreateTimeDesc(memberId, coinId, pageable);
        } else if (memberId != null) {
            page = memberDepositRepository.findByMemberIdOrderByCreateTimeDesc(memberId, pageable);
        } else if (coinId != null) {
            page = memberDepositRepository.findByCoinIdOrderByCreateTimeDesc(coinId, pageable);
        } else {
            page = memberDepositRepository.findAll(pageable);
        }

        List<Map<String, Object>> content = page.getContent().stream().map(record -> {
            Member member = memberRepository.findById(record.getMemberId()).orElse(null);
            Coin coin = coinRepository.findById(record.getCoinId()).orElse(null);
            
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.getId());
            map.put("memberId", record.getMemberId());
            map.put("username", member != null ? member.getUsername() : "?");
            map.put("coinId", record.getCoinId());
            map.put("unit", coin != null ? coin.getUnit() : "?");
            map.put("amount", record.getAmount());
            map.put("address", record.getAddress());
            map.put("txId", record.getTxId());
            map.put("status", record.getStatus());
            map.put("createTime", record.getCreateTime() != null ? record.getCreateTime().toString() : "");
            map.put("transferImage", record.getTransferImage() != null ? record.getTransferImage() : "");
            map.put("rejectReason", record.getRejectReason() != null ? record.getRejectReason() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    /** 拒绝用户提交的充值（可填写拒绝原因） */
    @PostMapping("/deposit/reject")
    @Transactional
    public Result<String> depositReject(@RequestBody Map<String, Object> body) {
        Long id = parseLongSafe(body.get("id"));
        if (id == null) return Result.fail(400, "缺少充值记录 id");
        com.vaultpi.asset.entity.MemberDeposit deposit = memberDepositRepository.findById(id).orElse(null);
        if (deposit == null) return Result.fail(404, "记录不存在");
        if (!"PENDING".equals(deposit.getStatus())) return Result.fail(400, "仅可拒绝待审核记录");
        String reason = body.get("reason") != null ? body.get("reason").toString().trim() : null;
        deposit.setStatus("REJECTED");
        deposit.setRejectReason(reason != null && !reason.isEmpty() ? reason : "未注明原因");
        memberDepositRepository.save(deposit);
        return Result.ok("已拒绝该笔充值");
    }

    /** 人工确认用户提交的充值（凭转账详情图入账） */
    @PostMapping("/deposit/confirm")
    @Transactional
    public Result<String> depositConfirm(@RequestBody Map<String, Object> body) {
        Long id = parseLongSafe(body.get("id"));
        if (id == null) return Result.fail(400, "缺少充值记录 id");
        com.vaultpi.asset.entity.MemberDeposit deposit = memberDepositRepository.findById(id).orElse(null);
        if (deposit == null) return Result.fail(404, "记录不存在");
        if (!"PENDING".equals(deposit.getStatus())) return Result.fail(400, "仅可确认待审核记录");
        if (deposit.getTransferImage() == null || deposit.getTransferImage().isEmpty()) return Result.fail(400, "该记录无转账详情图，请用手动充值");

        Member member = memberRepository.findById(deposit.getMemberId()).orElse(null);
        Coin coin = coinRepository.findById(deposit.getCoinId()).orElse(null);
        if (member == null || coin == null) return Result.fail(404, "会员或币种不存在");

        MemberWallet wallet = memberWalletRepository.findByMemberIdAndCoinId(deposit.getMemberId(), deposit.getCoinId()).orElse(null);
        if (wallet == null) {
            wallet = new MemberWallet();
            wallet.setMemberId(deposit.getMemberId());
            wallet.setCoinId(deposit.getCoinId());
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenBalance(BigDecimal.ZERO);
        }
        wallet.setBalance(wallet.getBalance().add(deposit.getAmount()));
        memberWalletRepository.save(wallet);

        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(deposit.getMemberId());
        tx.setSymbol(coin.getUnit());
        tx.setAmount(deposit.getAmount());
        tx.setType("RECHARGE");
        tx.setFee(BigDecimal.ZERO);
        tx.setCreateTime(Instant.now());
        memberTransactionRepository.save(tx);

        deposit.setStatus("CONFIRMED");
        memberDepositRepository.save(deposit);
        if ("USDT".equalsIgnoreCase(coin.getUnit())) {
            memberService.addRechargeAndUpdateVip(member, deposit.getAmount());
        }
        return Result.ok("确认成功，用户资金已到账");
    }

    @GetMapping("/transaction/page")
    public Result<Map<String, Object>> transactionPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String type) {
        var sort = Sort.by(Sort.Direction.DESC, "createTime");
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(50, pageSize)), sort);
        
        org.springframework.data.domain.Page<MemberTransaction> page;
        boolean hasUid = memberId != null;
        boolean hasSym = symbol != null && !symbol.isEmpty();
        boolean hasType = type != null && !type.isEmpty();

        if (hasUid && hasSym && hasType) {
            page = memberTransactionRepository.findByMemberIdAndSymbolAndTypeOrderByCreateTimeDesc(memberId, symbol, type, pageable);
        } else if (hasUid && hasSym) {
            page = memberTransactionRepository.findByMemberIdAndSymbolOrderByCreateTimeDesc(memberId, symbol, pageable);
        } else if (hasUid && hasType) {
            page = memberTransactionRepository.findByMemberIdAndTypeOrderByCreateTimeDesc(memberId, type, pageable);
        } else if (hasSym && hasType) {
            page = memberTransactionRepository.findBySymbolAndTypeOrderByCreateTimeDesc(symbol, type, pageable);
        } else if (hasUid) {
            page = memberTransactionRepository.findByMemberIdOrderByCreateTimeDesc(memberId, pageable);
        } else if (hasSym) {
            page = memberTransactionRepository.findBySymbolOrderByCreateTimeDesc(symbol, pageable);
        } else if (hasType) {
            page = memberTransactionRepository.findByTypeOrderByCreateTimeDesc(type, pageable);
        } else {
            page = memberTransactionRepository.findAll(pageable);
        }

        List<Map<String, Object>> content = page.getContent().stream().map(tx -> {
            Member member = memberRepository.findById(tx.getMemberId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", tx.getId());
            map.put("memberId", tx.getMemberId());
            map.put("username", member != null ? member.getUsername() : "?");
            map.put("symbol", tx.getSymbol());
            map.put("amount", tx.getAmount());
            map.put("type", tx.getType());
            map.put("fee", tx.getFee());
            map.put("createTime", tx.getCreateTime() != null ? tx.getCreateTime().toString() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "USDT") String unit) {
        // Prepare time ranges
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        java.time.Instant todayStart = today.atStartOfDay(zone).toInstant();
        java.time.Instant tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant();
        java.time.Instant yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant();
        java.time.Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        java.time.Instant nextMonthStart = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();

        // 仅统计正常用户（NORMAL），且只计：充值、提现、手动充值、扣除
        List<String> depositTypes = List.of("RECHARGE", "ADMIN_RECHARGE");
        List<String> withdrawTypes = List.of("WITHDRAW", "ADMIN_DEDUCT");

        BigDecimal todayDeposit = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, depositTypes, todayStart, tomorrowStart);
        if (todayDeposit == null) todayDeposit = BigDecimal.ZERO;

        BigDecimal todayWithdraw = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, withdrawTypes, todayStart, tomorrowStart);
        if (todayWithdraw == null) todayWithdraw = BigDecimal.ZERO;
        todayWithdraw = todayWithdraw.abs();

        BigDecimal yesterdayDeposit = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, depositTypes, yesterdayStart, todayStart);
        if (yesterdayDeposit == null) yesterdayDeposit = BigDecimal.ZERO;

        BigDecimal yesterdayWithdraw = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, withdrawTypes, yesterdayStart, todayStart);
        if (yesterdayWithdraw == null) yesterdayWithdraw = BigDecimal.ZERO;
        yesterdayWithdraw = yesterdayWithdraw.abs();

        BigDecimal monthDeposit = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, depositTypes, monthStart, nextMonthStart);
        if (monthDeposit == null) monthDeposit = BigDecimal.ZERO;

        BigDecimal monthWithdraw = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, withdrawTypes, monthStart, nextMonthStart);
        if (monthWithdraw == null) monthWithdraw = BigDecimal.ZERO;
        monthWithdraw = monthWithdraw.abs();

        Map<String, Object> map = new HashMap<>();
        map.put("todayDeposit", todayDeposit);
        map.put("todayWithdraw", todayWithdraw);
        map.put("todayPerformance", todayDeposit.subtract(todayWithdraw));
        
        map.put("yesterdayDeposit", yesterdayDeposit);
        map.put("yesterdayWithdraw", yesterdayWithdraw);
        map.put("yesterdayPerformance", yesterdayDeposit.subtract(yesterdayWithdraw));
        
        map.put("monthDeposit", monthDeposit);
        map.put("monthWithdraw", monthWithdraw);
        map.put("monthPerformance", monthDeposit.subtract(monthWithdraw));
        
        return Result.ok(map);
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "USDT") String unit) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate startDay = today.minusDays(days - 1);
        Instant startTime = startDay.atStartOfDay(zone).toInstant();
        Instant endTime = today.plusDays(1).atStartOfDay(zone).toInstant();

        // 仅：充值、提现、手动充值、扣除（与 stats 一致）
        List<String> types = List.of("RECHARGE", "ADMIN_RECHARGE", "WITHDRAW", "ADMIN_DEDUCT");
        List<MemberTransaction> list = memberTransactionRepository.findBySymbolAndTypeInAndCreateTimeBetweenExcludingInternal(
            unit, types, startTime, endTime
        );

        Map<LocalDate, BigDecimal> dailyMap = new HashMap<>();
        for (int i = 0; i < days; i++) {
            dailyMap.put(startDay.plusDays(i), BigDecimal.ZERO);
        }

        for (MemberTransaction tx : list) {
            LocalDate date = tx.getCreateTime().atZone(zone).toLocalDate();
            if (date.isBefore(startDay) || date.isAfter(today)) continue;
            dailyMap.merge(date, tx.getAmount(), BigDecimal::add);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = startDay.plusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", d.toString());
            item.put("value", dailyMap.get(d));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 财务统计-按真实会员分页：表头合计 + 按邮箱/UID/用户名搜索 + 时间筛选 */
    @GetMapping("/stats/page")
    public Result<Map<String, Object>> statsPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "USDT") String unit) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate startDay;
        LocalDate endDay;
        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            try {
                startDay = LocalDate.parse(startDate);
                endDay = LocalDate.parse(endDate);
                if (startDay.isAfter(endDay)) {
                    LocalDate t = startDay;
                    startDay = endDay;
                    endDay = t;
                }
            } catch (Exception e) {
                startDay = today.minusDays(29);
                endDay = today;
            }
        } else {
            startDay = today.minusDays(29);
            endDay = today;
        }
        Instant rangeStart = startDay.atStartOfDay(zone).toInstant();
        Instant rangeEnd = endDay.plusDays(1).atStartOfDay(zone).toInstant();

        String kw = searchKey != null ? searchKey.trim() : "";
        Pageable pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(100, pageSize)));
        Page<Member> memberPage = memberRepository.findRealMembers(kw, "", pageable);

        List<String> depositTypes = List.of("RECHARGE", "ADMIN_RECHARGE");
        List<String> withdrawTypes = List.of("WITHDRAW", "ADMIN_DEDUCT");
        List<Object[]> rechargeSums = memberTransactionRepository.sumAmountGroupByMemberIdRealMembers(unit, depositTypes, rangeStart, rangeEnd);
        List<Object[]> withdrawSums = memberTransactionRepository.sumAmountGroupByMemberIdRealMembers(unit, withdrawTypes, rangeStart, rangeEnd);

        java.util.Map<Long, BigDecimal> rechargeMap = new HashMap<>();
        for (Object[] row : rechargeSums) {
            Long mid = ((Number) row[0]).longValue();
            BigDecimal sum = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            rechargeMap.put(mid, sum);
        }
        java.util.Map<Long, BigDecimal> withdrawMap = new HashMap<>();
        for (Object[] row : withdrawSums) {
            Long mid = ((Number) row[0]).longValue();
            BigDecimal sum = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            withdrawMap.put(mid, sum.abs());
        }

        BigDecimal totalPeriodRecharge = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, depositTypes, rangeStart, rangeEnd);
        if (totalPeriodRecharge == null) totalPeriodRecharge = BigDecimal.ZERO;
        BigDecimal totalPeriodWithdraw = memberTransactionRepository.sumAmountBySymbolAndTypesAndTimeExcludingInternal(unit, withdrawTypes, rangeStart, rangeEnd);
        if (totalPeriodWithdraw == null) totalPeriodWithdraw = BigDecimal.ZERO;
        totalPeriodWithdraw = totalPeriodWithdraw.abs();

        List<Map<String, Object>> content = memberPage.getContent().stream().map(m -> {
            memberService.ensureUid(m);
            BigDecimal pr = rechargeMap.getOrDefault(m.getId(), BigDecimal.ZERO);
            BigDecimal pw = withdrawMap.getOrDefault(m.getId(), BigDecimal.ZERO);
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("uid", m.getUid());
            map.put("username", m.getUsername());
            map.put("email", m.getEmail() != null ? m.getEmail() : "");
            map.put("totalRecharge", m.getTotalRecharge() != null ? m.getTotalRecharge() : BigDecimal.ZERO);
            map.put("periodRecharge", pr);
            map.put("periodWithdraw", pw);
            map.put("periodPerformance", pr.subtract(pw));
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalMembers", memberPage.getTotalElements());
        summary.put("totalPeriodRecharge", totalPeriodRecharge);
        summary.put("totalPeriodWithdraw", totalPeriodWithdraw);
        summary.put("totalPeriodPerformance", totalPeriodRecharge.subtract(totalPeriodWithdraw));
        summary.put("startDate", startDay.toString());
        summary.put("endDate", endDay.toString());
        summary.put("unit", unit);

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", memberPage.getTotalElements());
        result.put("totalPages", memberPage.getTotalPages());
        result.put("summary", summary);
        return Result.ok(result);
    }

    @PostMapping("/deposit/manual")
    @Transactional
    public Result<String> depositManual(@RequestBody Map<String, Object> body) {
        Long memberId = parseLongSafe(body.get("memberId"));
        Long coinId = parseLongSafe(body.get("coinId"));
        BigDecimal amount = body.get("amount") != null ? new BigDecimal(body.get("amount").toString()) : BigDecimal.ZERO;
        String remark = body.get("remark") != null ? body.get("remark").toString() : "管理员充值";

        if (memberId == null || coinId == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail(400, "参数错误");
        }

        Member member = memberRepository.findById(memberId).orElse(null);
        Coin coin = coinRepository.findById(coinId).orElse(null);
        if (member == null || coin == null) {
            return Result.fail(404, "会员或币种不存在");
        }

        MemberWallet wallet = memberWalletRepository.findByMemberIdAndCoinId(memberId, coinId).orElse(null);
        if (wallet == null) {
            wallet = new MemberWallet();
            wallet.setMemberId(memberId);
            wallet.setCoinId(coinId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenBalance(BigDecimal.ZERO);
        }
        wallet.setBalance(wallet.getBalance().add(amount));
        memberWalletRepository.save(wallet);

        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setSymbol(coin.getUnit());
        tx.setAmount(amount);
        tx.setType("ADMIN_RECHARGE");
        tx.setFee(BigDecimal.ZERO);
        tx.setCreateTime(Instant.now());
        memberTransactionRepository.save(tx);

        if ("USDT".equalsIgnoreCase(coin.getUnit())) {
            memberService.addRechargeAndUpdateVip(member, amount);
        }
        return Result.ok("充值成功");
    }

    /** 充币地址列表：返回所有币种及其充币地址（C 端充值页按 unit 展示） */
    @GetMapping("/deposit-address/list")
    public Result<List<Map<String, Object>>> depositAddressList() {
        List<Map<String, Object>> list = coinRepository.findAll().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("unit", c.getUnit());
            m.put("name", c.getName() != null ? c.getName() : c.getUnit());
            m.put("depositAddress", c.getDepositAddress() != null ? c.getDepositAddress() : "");
            m.put("enable", Boolean.TRUE.equals(c.getEnable()));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    /** 新增充币地址：可指定 unit、name、address，若 unit 已存在则更新地址与名称 */
    @PostMapping("/deposit-address/add")
    public Result<Map<String, Object>> depositAddressAdd(@RequestBody Map<String, Object> body) {
        String unit = body.get("unit") != null ? body.get("unit").toString().trim() : null;
        if (unit == null || unit.isEmpty()) return Result.fail(400, "请填写币种（如 USDT、BTC）");
        String name = body.get("name") != null ? body.get("name").toString().trim() : null;
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        Coin coin = coinRepository.findByUnit(unit).orElse(null);
        if (coin == null) {
            coin = new Coin();
            coin.setUnit(unit);
            coin.setName(name != null && !name.isEmpty() ? name : unit);
            coin.setEnable(true);
        } else {
            if (name != null && !name.isEmpty()) coin.setName(name);
        }
        coin.setDepositAddress(address != null && !address.isEmpty() ? address : null);
        coin = coinRepository.save(coin);
        Map<String, Object> m = new HashMap<>();
        m.put("id", coin.getId());
        m.put("unit", coin.getUnit());
        m.put("name", coin.getName() != null ? coin.getName() : coin.getUnit());
        m.put("depositAddress", coin.getDepositAddress() != null ? coin.getDepositAddress() : "");
        return Result.ok(m);
    }

    /** 更新某币种的充币地址 */
    @PostMapping("/deposit-address/update")
    public Result<String> depositAddressUpdate(@RequestBody Map<String, Object> body) {
        Long coinId = body.get("coinId") != null ? parseLongSafe(body.get("coinId")) : null;
        String unit = body.get("unit") != null ? body.get("unit").toString().trim() : null;
        String address = body.get("address") != null ? body.get("address").toString().trim() : null;
        Coin coin = null;
        if (coinId != null) coin = coinRepository.findById(coinId).orElse(null);
        if (coin == null && unit != null && !unit.isEmpty()) coin = coinRepository.findByUnit(unit).orElse(null);
        if (coin == null) return Result.fail(400, "请指定有效币种（coinId 或 unit）");
        coin.setDepositAddress(address != null && !address.isEmpty() ? address : null);
        coinRepository.save(coin);
        return Result.ok("保存成功");
    }

    private static Long parseLongSafe(Object v) {
        if (v == null) return null;
        try {
            return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
