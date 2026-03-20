package com.vaultpi.user;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.ErrorCode;
import com.vaultpi.common.Result;
import com.vaultpi.common.service.AuditLogService;
import com.vaultpi.config.RequireLoginInterceptor;
import com.vaultpi.user.dto.LoginRequest;
import com.vaultpi.user.dto.MemberDTO;
import com.vaultpi.user.dto.RegisterRequest;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.service.MemberService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "登录与注册", description = "登录、注册、登录态检查、登出")
@RestController
@RequestMapping(value = { ApiPaths.BASE, ApiPaths.V1 })
public class LoginController {

    /** 与 CsrfFilter 中一致，用于登录/check 时写入 session 并返回给前端 */
    public static final String SESSION_CSRF_TOKEN = "csrfToken";

    private final MemberService memberService;
    private final MeterRegistry meterRegistry;
    private final AuditLogService auditLogService;

    public LoginController(MemberService memberService, MeterRegistry meterRegistry, AuditLogService auditLogService) {
        this.memberService = memberService;
        this.meterRegistry = meterRegistry;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "登录", description = "用户名密码登录，成功返回会员信息与 token/csrfToken")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                             HttpServletRequest request,
                                             HttpSession session) {
        Member member;
        try {
            member = memberService.login(req.getUsername(), req.getPassword());
        } catch (Exception e) {
            meterRegistry.counter("vaultpi.login.failures").increment();
            auditLogService.logLoginFail(req.getUsername(), request);
            throw e;
        }
        auditLogService.logLoginSuccess(member.getId(), member.getUsername(), request);
        // 会话固定防护：登录成功后更换 session ID；若容器不支持 changeSessionId，则新建 session 并复制属性后使旧 session 失效
        boolean sessionRotated = false;
        try {
            request.changeSessionId();
            sessionRotated = true;
        } catch (UnsupportedOperationException ignored) {
            // 备用：复制属性到新 session 并失效旧 session
            Map<String, Object> attrs = new HashMap<>();
            Enumeration<String> names = session.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                attrs.put(name, session.getAttribute(name));
            }
            session.invalidate();
            session = request.getSession(true);
            for (Map.Entry<String, Object> e : attrs.entrySet()) {
                session.setAttribute(e.getKey(), e.getValue());
            }
            sessionRotated = true;
        }
        if (!sessionRotated) {
            session = request.getSession(true);
        }
        session.setAttribute(RequireLoginInterceptor.SESSION_MEMBER_ID, member.getId());
        String csrfToken = UUID.randomUUID().toString();
        session.setAttribute(SESSION_CSRF_TOKEN, csrfToken);
        Map<String, Object> data = new HashMap<>();
        data.put("id", member.getId());
        data.put("username", member.getUsername());
        data.put("email", member.getEmail() != null ? member.getEmail() : "");
        data.put("vipLevel", member.getVipLevel() != null ? member.getVipLevel() : 0);
        data.put("token", session.getId());
        data.put("csrfToken", csrfToken);
        if ("ADMIN".equals(member.getRole()) && memberService.isDefaultWeakPassword(member)) {
            data.put("needPasswordChange", true);
        }
        return Result.ok(data);
    }

    @Operation(summary = "注册", description = "邮箱+用户名+密码注册")
    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest req) {
        String pwdErr = req.validatePassword();
        if (pwdErr != null) return Result.fail(ErrorCode.VALIDATION_FAILED, pwdErr);
        memberService.registerWithUsername(req.getEmail(), req.getUsername(), req.getPassword(), req.getInviteCode());
        return Result.ok("注册成功");
    }

    @Operation(summary = "检查登录态", description = "返回当前登录用户信息或 false")
    @PostMapping("/check/login")
    public Result<Object> checkLogin(HttpSession session) {
        Long memberId = (Long) session.getAttribute(RequireLoginInterceptor.SESSION_MEMBER_ID);
        if (memberId == null) {
            return Result.ok(false);
        }
        Optional<Member> memberOpt = memberService.findById(memberId);
        if (memberOpt.isEmpty()) {
            session.removeAttribute(RequireLoginInterceptor.SESSION_MEMBER_ID);
            return Result.ok(false);
        }
        Member m = memberOpt.get();
        MemberDTO dto = MemberDTO.from(m);
        Map<String, Object> data = new HashMap<>();
        data.put("id", dto.getId());
        data.put("username", dto.getUsername());
        data.put("email", dto.getEmail() != null ? dto.getEmail() : "");
        data.put("role", dto.getRole() != null ? dto.getRole() : "USER");
        data.put("nickname", dto.getNickname() != null ? dto.getNickname() : "");
        data.put("vipLevel", m.getVipLevel() != null ? m.getVipLevel() : 0);
        data.put("token", session.getId());
        Object csrfToken = session.getAttribute(SESSION_CSRF_TOKEN);
        if (csrfToken != null) data.put("csrfToken", csrfToken.toString());
        if ("ADMIN".equals(m.getRole()) && memberService.isDefaultWeakPassword(m)) {
            data.put("needPasswordChange", true);
        }
        return Result.ok(data);
    }

    @Operation(summary = "登出", description = "销毁当前会话")
    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        session.invalidate();
        return Result.ok("已退出");
    }
}
