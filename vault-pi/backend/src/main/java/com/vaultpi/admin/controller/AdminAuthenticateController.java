package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.entity.MemberApplication;
import com.vaultpi.user.repository.MemberApplicationRepository;
import com.vaultpi.user.repository.MemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/authenticate", ApiPaths.V1 + "/admin/authenticate" })
public class AdminAuthenticateController {

    private final MemberApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;

    public AdminAuthenticateController(MemberApplicationRepository applicationRepository,
                                       MemberRepository memberRepository) {
        this.applicationRepository = applicationRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String auditStatus) {
        var sort = Sort.by(Sort.Direction.DESC, "createTime");
        var pageable = PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, Math.min(50, pageSize)), sort);
        var page = (auditStatus != null && !auditStatus.isEmpty())
            ? applicationRepository.findByAuditStatusOrderByCreateTimeDesc(auditStatus, pageable)
            : applicationRepository.findAll(pageable);
        List<Map<String, Object>> content = page.getContent().stream().map(app -> {
            Member member = memberRepository.findById(app.getMemberId()).orElse(null);
            String username = member != null ? member.getUsername() : "?";
            return Map.<String, Object>of(
                "id", app.getId(),
                "memberId", app.getMemberId(),
                "username", username,
                "realName", app.getRealName() != null ? app.getRealName() : "",
                "idCard", app.getIdCard() != null ? app.getIdCard() : "",
                "auditStatus", app.getAuditStatus(),
                "rejectReason", app.getRejectReason() != null ? app.getRejectReason() : "",
                "createTime", app.getCreateTime() != null ? app.getCreateTime().toString() : ""
            );
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long id) {
        MemberApplication app = applicationRepository.findById(id).orElse(null);
        if (app == null) return Result.fail(404, "申请不存在");
        Member member = memberRepository.findById(app.getMemberId()).orElse(null);
        Map<String, Object> map = new HashMap<>();
        map.put("id", app.getId());
        map.put("memberId", app.getMemberId());
        map.put("username", member != null ? member.getUsername() : "");
        map.put("realName", app.getRealName());
        map.put("idCard", app.getIdCard());
        map.put("identityCardImgFront", app.getIdentityCardImgFront());
        map.put("identityCardImgReverse", app.getIdentityCardImgReverse());
        map.put("identityCardImgInHand", app.getIdentityCardImgInHand());
        map.put("auditStatus", app.getAuditStatus());
        map.put("rejectReason", app.getRejectReason());
        map.put("createTime", app.getCreateTime() != null ? app.getCreateTime().toString() : null);
        map.put("updateTime", app.getUpdateTime() != null ? app.getUpdateTime().toString() : null);
        return Result.ok(map);
    }

    @PostMapping("/audit")
    public Result<String> audit(@RequestBody Map<String, Object> body) {
        Long id = parseLongSafe(body != null ? body.get("id") : null);
        String status = body != null && body.get("auditStatus") != null ? body.get("auditStatus").toString().trim() : null;
        String rejectReason = body != null && body.get("rejectReason") != null ? body.get("rejectReason").toString().trim() : null;
        if (id == null || status == null) return Result.fail(400, "缺少 id 或 auditStatus");
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            return Result.fail(400, "auditStatus 只能为 APPROVED 或 REJECTED");
        }
        MemberApplication app = applicationRepository.findById(id).orElse(null);
        if (app == null) return Result.fail(404, "申请不存在");
        if (!"PENDING".equals(app.getAuditStatus())) return Result.fail(400, "该申请已审核");
        app.setAuditStatus(status);
        app.setRejectReason("REJECTED".equals(status) ? rejectReason : null);
        app.setUpdateTime(Instant.now());
        applicationRepository.save(app);
        if ("APPROVED".equals(status)) {
            Member member = memberRepository.findById(app.getMemberId()).orElse(null);
            if (member != null) {
                member.setRealName(app.getRealName());
                member.setIdCard(app.getIdCard());
                memberRepository.save(member);
            }
        }
        return Result.ok("审核成功");
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
