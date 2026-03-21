package com.vaultpi.admin.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpi.asset.entity.MemberTransaction;
import com.vaultpi.asset.repository.MemberTransactionRepository;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.system.entity.SystemConfig;
import com.vaultpi.system.repository.SystemConfigRepository;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.service.MemberService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端 - 交易对列表、会员列表（暂不校验管理员身份）
 */
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin", ApiPaths.V1 + "/admin" })
public class AdminDataController {

    private static final String INVITE_COMMISSION_CONFIG_KEY = "invite_commission_config";
    private static final List<String> DEPOSIT_TYPES = List.of("RECHARGE", "ADMIN_RECHARGE");
    private static final List<String> WITHDRAW_TYPES = List.of("WITHDRAW", "ADMIN_DEDUCT");

    private final ExchangeCoinRepository exchangeCoinRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final MemberTransactionRepository transactionRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminDataController(ExchangeCoinRepository exchangeCoinRepository,
                               MemberRepository memberRepository,
                               MemberService memberService,
                               MemberTransactionRepository transactionRepository,
                               SystemConfigRepository systemConfigRepository,
                               PasswordEncoder passwordEncoder) {
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.transactionRepository = transactionRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final String SESSION_MEMBER_ID = "memberId";

    /** 当前登录管理员信息（用于 B 端侧栏权限过滤） */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        Member m = memberRepository.findById(memberId).orElse(null);
        if (m == null || !"ADMIN".equals(m.getRole())) return Result.fail(403, "非管理员");
        Map<String, Object> data = new HashMap<>();
        data.put("username", m.getUsername());
        data.put("adminDisplayName", m.getAdminDisplayName() != null ? m.getAdminDisplayName() : m.getUsername());
        List<String> perms = parsePermissions(m.getAdminPermissions());
        data.put("adminPermissions", perms);
        data.put("id", m.getId());
        data.put("adminRole", m.getAdminRole() != null ? m.getAdminRole() : "");
        return Result.ok(data);
    }

    /** 当前管理员更新个人资料：显示名称、修改密码 */
    @PostMapping("/me/update")
    public Result<String> meUpdate(HttpSession session, @RequestBody Map<String, Object> body) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        Member m = memberRepository.findById(memberId).orElse(null);
        if (m == null || !"ADMIN".equals(m.getRole())) return Result.fail(403, "非管理员");
        String adminDisplayName = body.get("adminDisplayName") != null ? body.get("adminDisplayName").toString().trim() : null;
        if (adminDisplayName != null) m.setAdminDisplayName(adminDisplayName.isEmpty() ? null : adminDisplayName);
        String newPassword = body.get("newPassword") != null ? body.get("newPassword").toString() : null;
        if (newPassword != null && !newPassword.isBlank()) {
            String pwdErr = com.vaultpi.common.PasswordPolicy.validate(newPassword);
            if (pwdErr != null) return Result.fail(400, pwdErr);
            String currentPassword = body.get("currentPassword") != null ? body.get("currentPassword").toString() : null;
            if (currentPassword == null || currentPassword.isBlank()) return Result.fail(400, "请输入当前密码");
            if (!passwordEncoder.matches(currentPassword, m.getPassword())) return Result.fail(400, "当前密码错误");
            m.setPassword(passwordEncoder.encode(newPassword));
        }
        memberRepository.save(m);
        return Result.ok("保存成功");
    }

    private List<String> parsePermissions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @GetMapping("/exchange-coin/list")
    public Result<List<ExchangeCoin>> exchangeCoinList() {
        return Result.ok(exchangeCoinRepository.findAll());
    }

    /** 获取某管理员邀请树下所有会员 ID（含多级下级） */
    private List<Long> getInviteTreeMemberIds(Long adminId) {
        List<Long> ids = new ArrayList<>();
        List<Long> current = memberRepository.findByParentIdIn(Collections.singletonList(adminId)).stream().map(Member::getId).collect(Collectors.toList());
        while (!current.isEmpty()) {
            ids.addAll(current);
            current = memberRepository.findByParentIdIn(current).stream().map(Member::getId).collect(Collectors.toList());
        }
        return ids;
    }

    @GetMapping("/member/page")
    public Result<Map<String, Object>> memberPage(HttpSession session,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) String statusFilter) {
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, pageSize));
        String kw = searchKey != null ? searchKey.trim() : "";
        String status = statusFilter != null ? statusFilter.trim() : "";
        org.springframework.data.domain.Page<Member> page;
        Long sessionId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        Member sessionAdmin = sessionId != null ? memberRepository.findById(sessionId).orElse(null) : null;
        if (sessionAdmin != null && "ZHUGUAN".equals(sessionAdmin.getAdminRole())) {
            List<Long> treeIds = getInviteTreeMemberIds(sessionAdmin.getId());
            if (treeIds.isEmpty()) {
                page = new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, 0);
            } else {
                page = memberRepository.findMembersByIdIn(treeIds, kw, status, pageable);
            }
        } else {
            page = memberRepository.findMembers(kw, status, pageable);
        }
        List<Map<String, Object>> content = page.getContent().stream()
            .map(m -> {
                memberService.ensureUid(m);
                memberService.ensureInternalInviteCode(m);
                Map<String, Object> map = new HashMap<>();
                map.put("id", m.getId());
                map.put("uid", m.getUid());
                map.put("userType", m.getUserType() != null ? m.getUserType() : "NORMAL");
                map.put("username", m.getUsername());
                map.put("email", m.getEmail());
                map.put("phone", m.getPhone());
                map.put("nickname", m.getNickname());
                map.put("realName", m.getRealName());
                map.put("parentId", m.getParentId());
                map.put("inviteCode", m.getInviteCode());
                map.put("status", m.getStatus());
                map.put("vipLevel", m.getVipLevel() != null ? m.getVipLevel() : 0);
                map.put("totalRecharge", m.getTotalRecharge() != null ? m.getTotalRecharge() : java.math.BigDecimal.ZERO);
                map.put("registrationTime", m.getRegistrationTime() != null ? m.getRegistrationTime().toString() : "");
                if (m.getParentId() != null) {
                    memberRepository.findById(m.getParentId()).ifPresent(p -> {
                        map.put("parentUid", p.getUid());
                        map.put("parentUsername", p.getUsername());
                    });
                }
                return map;
            })
            .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    /** 邀请模块就绪检测（GET 返回 ok 表示邀请接口已加载，便于排查 404） */
    @GetMapping("/invite")
    public Result<String> inviteReady() {
        return Result.ok("ok");
    }

    /** 邀请/推广统计：内部用户=推广员；主管仅看自己推广树且不可编辑佣金 */
    @GetMapping("/invite/stat")
    public Result<Map<String, Object>> inviteStat(HttpSession session,
                                                   @RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) String kw) {
        Long sessionId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        Member sessionAdmin = sessionId != null ? memberRepository.findById(sessionId).orElse(null) : null;
        boolean isZhuguan = sessionAdmin != null && "ZHUGUAN".equals(sessionAdmin.getAdminRole());

        List<Member> all = memberRepository.findAll();
        List<Member> promoters = all.stream().filter(m -> "INTERNAL".equals(m.getUserType())).collect(Collectors.toList());
        if (isZhuguan) {
            promoters = promoters.stream().filter(p -> p.getId().equals(sessionAdmin.getId())).collect(Collectors.toList());
        }
        Map<Long, List<Member>> subordinatesByPromoter = new HashMap<>();
        for (Member m : all) {
            if (m.getParentId() != null) {
                subordinatesByPromoter.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(m);
            }
        }
        Map<Long, BigDecimal> performanceCache = new HashMap<>();
        final String kwTrim = kw != null ? kw.trim() : "";
        List<Map<String, Object>> list = promoters.stream()
            .filter(m -> kwTrim.isEmpty() || m.getUsername().contains(kwTrim) || String.valueOf(m.getId()).contains(kwTrim))
            .map(p -> {
                List<Member> subs = subordinatesByPromoter.getOrDefault(p.getId(), Collections.emptyList());
                if (isZhuguan && p.getId().equals(sessionAdmin.getId())) {
                    List<Long> treeIds = getInviteTreeMemberIds(p.getId());
                    subs = treeIds.isEmpty() ? Collections.emptyList() : memberRepository.findAllById(treeIds);
                }
                BigDecimal totalSubPerformance = BigDecimal.ZERO;
                for (Member sub : subs) {
                    BigDecimal perf = performanceCache.computeIfAbsent(sub.getId(), this::memberPerformance);
                    totalSubPerformance = totalSubPerformance.add(perf);
                }
                long directCount = isZhuguan && p.getId().equals(sessionAdmin.getId())
                    ? subordinatesByPromoter.getOrDefault(p.getId(), Collections.emptyList()).size()
                    : subs.size();
                BigDecimal totalCommission = transactionRepository.findByMemberId(p.getId()).stream()
                    .filter(t -> "PROMOTION".equals(t.getType()))
                    .map(MemberTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                Map<String, Object> item = new HashMap<>();
                item.put("memberId", p.getId());
                item.put("username", p.getUsername());
                item.put("inviteCode", p.getInviteCode());
                item.put("directCount", directCount);
                item.put("totalSubordinatePerformance", totalSubPerformance.setScale(2, RoundingMode.HALF_UP));
                item.put("totalCommission", totalCommission);
                item.put("hasCustomCommission", hasPromoterCommissionConfig(p.getId()));
                return item;
            })
            .sorted((a, b) -> ((BigDecimal) b.get("totalSubordinatePerformance")).compareTo((BigDecimal) a.get("totalSubordinatePerformance")))
            .collect(Collectors.toList());
        int from = Math.max(0, (pageNo - 1) * pageSize);
        int to = Math.min(list.size(), from + pageSize);
        List<Map<String, Object>> content = from < list.size() ? list.subList(from, to) : new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", (long) list.size());
        result.put("totalPages", list.isEmpty() ? 0 : (list.size() + pageSize - 1) / pageSize);
        result.put("commissionConfig", getCommissionConfig(null));
        result.put("canEditCommission", !isZhuguan);
        return Result.ok(result);
    }

    @GetMapping("/invite/children")
    public Result<List<Map<String, Object>>> inviteChildren(@RequestParam Long parentId) {
        List<Member> children = memberRepository.findAll().stream()
            .filter(m -> parentId.equals(m.getParentId()))
            .collect(Collectors.toList());
        Map<String, Object> config = getCommissionConfig(parentId);
        List<Map<String, Object>> data = children.stream().map(m -> {
            BigDecimal perf = memberPerformance(m.getId());
            double rate = getRateForPerformance(perf, config);
            Map<String, Object> item = new HashMap<>();
            item.put("memberId", m.getId());
            item.put("username", m.getUsername());
            item.put("userType", m.getUserType() != null ? m.getUserType() : "NORMAL");
            item.put("registrationTime", m.getRegistrationTime());
            item.put("status", m.getStatus());
            item.put("subordinatePerformance", perf.setScale(2, RoundingMode.HALF_UP));
            item.put("appliedCommissionRate", rate);
            return item;
        }).collect(Collectors.toList());
        return Result.ok(data);
    }

    /** 获取佣金配置：promoterId 为空则返回全局默认，否则返回该推广员单独配置（无则回退全局） */
    @GetMapping("/invite/commission")
    public Result<Map<String, Object>> getInviteCommission(@RequestParam(required = false) Long promoterId) {
        return Result.ok(getCommissionConfig(promoterId));
    }

    @PostMapping("/invite/commission")
    public Result<String> saveInviteCommission(@RequestBody Map<String, Object> body) {
        try {
            Long promoterId = body.get("promoterId") != null ? Long.valueOf(body.get("promoterId").toString()) : null;
            Map<String, Object> config = new HashMap<>();
            if (body.get("fixedRate") != null) config.put("fixedRate", toNumber(body.get("fixedRate")));
            else config.put("fixedRate", 0.05);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tiers = (List<Map<String, Object>>) body.get("tiers");
            if (tiers != null) {
                List<Map<String, Object>> normalized = new ArrayList<>();
                for (Map<String, Object> t : tiers) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("minPerformance", t.get("minPerformance") != null ? toNumber(t.get("minPerformance")) : 0);
                    row.put("maxPerformance", t.get("maxPerformance") != null ? toNumber(t.get("maxPerformance")) : null);
                    row.put("rate", t.get("rate") != null ? toNumber(t.get("rate")) : 0.05);
                    normalized.add(row);
                }
                config.put("tiers", normalized);
            } else {
                config.put("tiers", Collections.emptyList());
            }
            String json = objectMapper.writeValueAsString(config);
            String configKey = promoterId != null ? "invite_commission_promoter_" + promoterId : INVITE_COMMISSION_CONFIG_KEY;
            SystemConfig sc = systemConfigRepository.findById(configKey).orElse(new SystemConfig());
            sc.setId(configKey);
            sc.setValue(json);
            sc.setRemark(promoterId != null ? "推广员 " + promoterId + " 单独佣金" : "推广员佣金全局默认");
            systemConfigRepository.save(sc);
            return Result.ok("保存成功");
        } catch (Exception e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 会员业绩 = 充值+手动充值 - 提现-扣除（仅统计四类流水） */
    private BigDecimal memberPerformance(Long memberId) {
        List<MemberTransaction> list = transactionRepository.findByMemberId(memberId);
        BigDecimal deposit = list.stream().filter(t -> DEPOSIT_TYPES.contains(t.getType())).map(MemberTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdraw = list.stream().filter(t -> WITHDRAW_TYPES.contains(t.getType())).map(t -> t.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return deposit.subtract(withdraw);
    }

    private boolean hasPromoterCommissionConfig(Long promoterId) {
        if (promoterId == null) return false;
        return systemConfigRepository.findById("invite_commission_promoter_" + promoterId).isPresent();
    }

    /** promoterId 为 null 时返回全局默认；否则先查该推广员单独配置，无则返回全局 */
    private Map<String, Object> getCommissionConfig(Long promoterId) {
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("fixedRate", 0.05);
        defaultConfig.put("tiers", Collections.<Map<String, Object>>emptyList());
        if (promoterId != null) {
            Optional<SystemConfig> promoterConfig = systemConfigRepository.findById("invite_commission_promoter_" + promoterId);
            if (promoterConfig.isPresent() && promoterConfig.get().getValue() != null && !promoterConfig.get().getValue().isBlank()) {
                try {
                    return objectMapper.readValue(promoterConfig.get().getValue(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) { }
            }
        }
        return systemConfigRepository.findById(INVITE_COMMISSION_CONFIG_KEY)
            .map(sc -> {
                try {
                    return objectMapper.readValue(sc.getValue(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    return defaultConfig;
                }
            })
            .orElse(defaultConfig);
    }

    private double getRateForPerformance(BigDecimal performance, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) config.get("tiers");
        double fixed = config.get("fixedRate") != null ? ((Number) config.get("fixedRate")).doubleValue() : 0.05;
        if (tiers != null && !tiers.isEmpty()) {
            double perf = performance.doubleValue();
            for (Map<String, Object> t : tiers) {
                double min = t.get("minPerformance") != null ? ((Number) t.get("minPerformance")).doubleValue() : 0;
                Object maxObj = t.get("maxPerformance");
                double max = maxObj == null ? Double.MAX_VALUE : ((Number) maxObj).doubleValue();
                if (perf >= min && perf < max) {
                    return t.get("rate") != null ? ((Number) t.get("rate")).doubleValue() : fixed;
                }
            }
        }
        return fixed;
    }

    private Number toNumber(Object o) {
        if (o instanceof Number) return (Number) o;
        if (o instanceof String) return Double.parseDouble((String) o);
        throw new IllegalArgumentException("Invalid number: " + o);
    }
}
