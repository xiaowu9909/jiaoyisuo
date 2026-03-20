package com.vaultpi.admin.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.PasswordPolicy;
import com.vaultpi.common.Result;
import com.vaultpi.system.entity.SystemConfig;
import com.vaultpi.system.repository.SystemConfigRepository;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.service.MemberService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/system", ApiPaths.V1 + "/admin/system" })
public class AdminSystemController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final SystemConfigRepository configRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberService memberService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminSystemController(SystemConfigRepository configRepository,
                                 MemberRepository memberRepository,
                                 PasswordEncoder passwordEncoder,
                                 MemberService memberService) {
        this.configRepository = configRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberService = memberService;
    }

    /** 管理员列表（仅 role=ADMIN 的会员） */
    @GetMapping("/admins")
    public Result<List<Map<String, Object>>> listAdmins(HttpSession session) {
        if (session.getAttribute(SESSION_MEMBER_ID) == null) return Result.fail(401, "请先登录");
        List<Member> admins = memberRepository.findByRole("ADMIN");
        List<Map<String, Object>> list = admins.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("username", m.getUsername());
            map.put("adminDisplayName", m.getAdminDisplayName() != null ? m.getAdminDisplayName() : "");
            map.put("adminPermissions", parsePermissions(m.getAdminPermissions()));
            map.put("adminRole", m.getAdminRole() != null ? m.getAdminRole() : "");
            map.put("status", m.getStatus() != null ? m.getStatus() : "NORMAL");
            map.put("boundMemberId", m.getBoundMemberId());
            if (m.getBoundMemberId() != null) {
                memberRepository.findById(m.getBoundMemberId()).ifPresent(bound -> {
                    map.put("boundMemberUsername", bound.getUsername());
                    map.put("boundMemberUid", bound.getUid());
                });
            }
            return map;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    /** 新增二级管理员 */
    @PostMapping("/admin/add")
    public Result<Map<String, Object>> addAdmin(HttpSession session, @RequestBody Map<String, Object> body) {
        if (session.getAttribute(SESSION_MEMBER_ID) == null) return Result.fail(401, "请先登录");
        String username = body.get("username") != null ? body.get("username").toString().trim() : null;
        String password = body.get("password") != null ? body.get("password").toString() : null;
        String adminDisplayName = body.get("adminDisplayName") != null ? body.get("adminDisplayName").toString().trim() : null;
        @SuppressWarnings("unchecked")
        List<String> perms = body.get("permissions") instanceof List ? (List<String>) body.get("permissions") : Collections.emptyList();
        if (username == null || username.isEmpty()) return Result.fail(400, "请填写登录名");
        String pwdErr = PasswordPolicy.validate(password);
        if (pwdErr != null) return Result.fail(400, pwdErr);
        if (memberRepository.findByUsername(username).isPresent()) return Result.fail(400, "该登录名已存在");
        Member admin = new Member();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setStatus("NORMAL");
        admin.setRole("ADMIN");
        admin.setUserType("INTERNAL");
        admin.setRegistrationTime(Instant.now());
        admin.setAdminDisplayName(adminDisplayName != null && !adminDisplayName.isEmpty() ? adminDisplayName : null);
        admin.setAdminPermissions(serializePermissions(perms));
        String adminRole = body.get("adminRole") != null ? body.get("adminRole").toString().trim() : null;
        if (adminRole != null && ("KEFU".equals(adminRole) || "ZHUGUAN".equals(adminRole) || adminRole.isEmpty())) {
            admin.setAdminRole(adminRole.isEmpty() ? null : adminRole);
        }
        Long boundMemberId = body.get("boundMemberId") != null ? toLong(body.get("boundMemberId")) : null;
        if (boundMemberId != null) {
            Member bound = memberRepository.findById(boundMemberId).orElse(null);
            if (bound == null || "ADMIN".equals(bound.getRole()))
                return Result.fail(400, "绑定会员不存在或不能绑定后台管理员账号");
            admin.setBoundMemberId(boundMemberId);
        } else {
            admin.setBoundMemberId(null);
        }
        admin = memberRepository.save(admin);
        if ("INTERNAL".equals(admin.getUserType())) {
            memberService.ensureInternalInviteCode(admin);
            admin = memberRepository.save(admin);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", admin.getId());
        data.put("username", admin.getUsername());
        data.put("adminDisplayName", admin.getAdminDisplayName());
        data.put("adminPermissions", perms);
        data.put("adminRole", admin.getAdminRole());
        data.put("boundMemberId", admin.getBoundMemberId());
        return Result.ok(data);
    }

    /** 更新二级管理员（显示名称与权限） */
    @PostMapping("/admin/update")
    public Result<String> updateAdmin(HttpSession session, @RequestBody Map<String, Object> body) {
        if (session.getAttribute(SESSION_MEMBER_ID) == null) return Result.fail(401, "请先登录");
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        if (id == null) return Result.fail(400, "缺少管理员 id");
        Member admin = memberRepository.findById(id).orElse(null);
        if (admin == null || !"ADMIN".equals(admin.getRole())) return Result.fail(404, "管理员不存在");
        String adminDisplayName = body.get("adminDisplayName") != null ? body.get("adminDisplayName").toString().trim() : null;
        @SuppressWarnings("unchecked")
        List<String> perms = body.get("permissions") instanceof List ? (List<String>) body.get("permissions") : null;
        admin.setAdminDisplayName(adminDisplayName != null && !adminDisplayName.isEmpty() ? adminDisplayName : null);
        if (perms != null) admin.setAdminPermissions(serializePermissions(perms));
        String adminRole = body.get("adminRole") != null ? body.get("adminRole").toString().trim() : null;
        if (adminRole != null && ("KEFU".equals(adminRole) || "ZHUGUAN".equals(adminRole) || adminRole.isEmpty())) {
            admin.setAdminRole(adminRole.isEmpty() ? null : adminRole);
        }
        String newPassword = body.get("password") != null ? body.get("password").toString() : null;
        if (newPassword != null && !newPassword.isBlank()) {
            String pwdErr = PasswordPolicy.validate(newPassword);
            if (pwdErr != null) return Result.fail(400, "新密码：" + pwdErr);
            admin.setPassword(passwordEncoder.encode(newPassword));
        }
        if (body.containsKey("boundMemberId")) {
            Long boundMemberId = body.get("boundMemberId") != null ? toLong(body.get("boundMemberId")) : null;
            if (boundMemberId != null) {
                Member bound = memberRepository.findById(boundMemberId).orElse(null);
                if (bound == null || "ADMIN".equals(bound.getRole()))
                    return Result.fail(400, "绑定会员不存在或不能绑定后台管理员账号");
                admin.setBoundMemberId(boundMemberId);
            } else {
                admin.setBoundMemberId(null);
            }
        }
        memberRepository.save(admin);
        return Result.ok("保存成功");
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(o.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private List<String> parsePermissions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String serializePermissions(List<String> perms) {
        if (perms == null || perms.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(perms);
        } catch (Exception e) {
            return "[]";
        }
    }

    @GetMapping("/config/list")
    public Result<List<SystemConfig>> list() {
        return Result.ok(configRepository.findAll());
    }

    @PostMapping("/config/update")
    public Result<SystemConfig> update(@RequestBody Map<String, String> body) {
        String id = body.get("id");
        String value = body.get("value");
        if (id == null || id.isEmpty()) return Result.fail(400, "Key cannot be empty");
        
        SystemConfig config = configRepository.findById(id).orElse(new SystemConfig());
        config.setId(id);
        config.setValue(value != null ? value : "");
        if (body.containsKey("remark")) config.setRemark(body.get("remark"));
        if (body.containsKey("groupName")) config.setGroupName(body.get("groupName"));
        
        config = configRepository.save(config);
        return Result.ok(config);
    }
    
    @PostMapping("/config/batch-update")
    public Result<String> batchUpdate(@RequestBody List<Map<String, String>> list) {
        for (Map<String, String> item : list) {
            String id = item.get("id");
            String value = item.get("value");
            if (id != null) {
                SystemConfig config = configRepository.findById(id).orElse(new SystemConfig());
                config.setId(id);
                config.setValue(value != null ? value : "");
                configRepository.save(config);
            }
        }
        return Result.ok("Success");
    }
}
