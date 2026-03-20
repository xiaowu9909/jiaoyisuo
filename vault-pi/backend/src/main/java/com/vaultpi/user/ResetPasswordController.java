package com.vaultpi.user;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.common.service.AuditLogService;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc/reset", ApiPaths.V1 + "/uc/reset" })
public class ResetPasswordController {

    private static final String REDIS_KEY_PREFIX = "vaultpi:reset:";
    private static final int CODE_EXPIRE_SECONDS = 600;
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate redisTemplate; // 无 Redis 时为 null
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final AuditLogService auditLogService;

    @Value("${app.reset-password.dev-code:}")
    private String devCode;

    public ResetPasswordController(@Autowired(required = false) StringRedisTemplate redisTemplate,
                                   MemberRepository memberRepository,
                                   MemberService memberService,
                                   AuditLogService auditLogService) {
        this.redisTemplate = redisTemplate;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/email/code")
    public Result<String> sendCode(@RequestBody Map<String, String> body) {
        if (redisTemplate == null) {
            return Result.fail(503, "找回密码功能需要 Redis，当前不可用");
        }
        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank()) {
            return Result.fail(400, "请填写邮箱");
        }
        String emailNorm = email.trim().toLowerCase();
        Optional<Member> member = memberRepository.findByEmail(emailNorm);
        if (member.isEmpty()) {
            return Result.fail(400, "该邮箱未注册");
        }
        String code = devCode != null && !devCode.isEmpty()
            ? devCode
            : String.format("%0" + CODE_LENGTH + "d", ThreadLocalRandom.current().nextInt((int) Math.pow(10, CODE_LENGTH)));
        String key = REDIS_KEY_PREFIX + emailNorm;
        redisTemplate.opsForValue().set(key, code, java.time.Duration.ofSeconds(CODE_EXPIRE_SECONDS));
        if (devCode != null && !devCode.isEmpty()) {
            return Result.ok("验证码已发送（开发模式：" + code + "）");
        }
        return Result.ok("验证码已发送至邮箱");
    }

    @PostMapping("/login/password")
    public Result<String> resetPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (redisTemplate == null) {
            return Result.fail(503, "找回密码功能需要 Redis，当前不可用");
        }
        String email = body != null ? body.get("email") : null;
        String code = body != null ? body.get("code") : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        if (email == null || email.isBlank() || code == null || code.isBlank() || newPassword == null || newPassword.isBlank()) {
            return Result.fail(400, "请填写邮箱、验证码和新密码");
        }
        String pwdErr = com.vaultpi.common.PasswordPolicy.validate(newPassword);
        if (pwdErr != null) return Result.fail(400, pwdErr);
        String emailNorm = email.trim().toLowerCase();
        String key = REDIS_KEY_PREFIX + emailNorm;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code.trim())) {
            return Result.fail(400, "验证码错误或已过期");
        }
        Member member = memberRepository.findByEmail(emailNorm).orElse(null);
        if (member == null) {
            return Result.fail(400, "该邮箱未注册");
        }
        memberService.updatePassword(member.getId(), newPassword);
        auditLogService.logPasswordReset(member.getId(), request);
        redisTemplate.delete(key);
        return Result.ok("密码已重置，请使用新密码登录");
    }
}
