package com.vaultpi.user;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.Result;
import com.vaultpi.common.SessionUtil;
import com.vaultpi.common.service.AuditLogService;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.entity.MemberApplication;
import com.vaultpi.user.repository.MemberApplicationRepository;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc", ApiPaths.V1 + "/uc" })
public class UcProfileController {

    private final MemberRepository memberRepository;
    private final MemberApplicationRepository memberApplicationRepository;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UcProfileController(MemberRepository memberRepository,
                               MemberApplicationRepository memberApplicationRepository,
                               MemberService memberService,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService) {
        this.memberRepository = memberRepository;
        this.memberApplicationRepository = memberApplicationRepository;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/safe/update-password")
    public Result<String> updatePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        String oldPassword = body != null ? body.get("oldPassword") : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        if (oldPassword == null || newPassword == null) {
            return Result.fail(400, "请填写原密码和新密码");
        }
        String pwdErr = com.vaultpi.common.PasswordPolicy.validate(newPassword);
        if (pwdErr != null) return Result.fail(400, pwdErr);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return Result.fail(404, "用户不存在");
        if (!passwordEncoder.matches(oldPassword, member.getPassword())) {
            return Result.fail(400, "原密码错误");
        }
        memberService.updatePassword(memberId, newPassword);
        auditLogService.logPasswordUpdate(memberId, request);
        return Result.ok("密码已修改");
    }

    @GetMapping("/account")
    public Result<Map<String, Object>> getAccount(HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return Result.fail(404, "用户不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("username", member.getUsername());
        m.put("email", member.getEmail());
        m.put("phone", member.getPhone());
        m.put("nickname", member.getNickname());
        m.put("realName", member.getRealName());
        return Result.ok(m);
    }

    @PostMapping("/account")
    public Result<String> updateAccount(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return Result.fail(404, "用户不存在");
        if (body != null && body.containsKey("nickname")) {
            member.setNickname(body.get("nickname") != null ? body.get("nickname").trim() : null);
        }
        memberRepository.save(member);
        return Result.ok("已保存");
    }

    @PostMapping("/authenticate")
    public Result<String> submitAuthenticate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        String realName = body != null ? (String) body.get("realName") : null;
        String idCard = body != null ? (String) body.get("idCard") : null;
        String imgFront = body != null ? (String) body.get("identityCardImgFront") : null;
        String imgReverse = body != null ? (String) body.get("identityCardImgReverse") : null;
        String imgInHand = body != null ? (String) body.get("identityCardImgInHand") : null;
        if (realName == null || realName.isBlank() || idCard == null || idCard.isBlank()) {
            return Result.fail(400, "请填写真实姓名和身份证号");
        }
        if (imgFront == null || imgFront.isBlank() || imgReverse == null || imgReverse.isBlank() || imgInHand == null || imgInHand.isBlank()) {
            return Result.fail(400, "请上传证件正面、反面和手持证件三张图片");
        }
        MemberApplication app = new MemberApplication();
        app.setMemberId(memberId);
        app.setRealName(realName.trim());
        app.setIdCard(idCard.trim());
        app.setIdentityCardImgFront(imgFront != null ? imgFront.trim() : "");
        app.setIdentityCardImgReverse(imgReverse != null ? imgReverse.trim() : "");
        app.setIdentityCardImgInHand(imgInHand != null ? imgInHand.trim() : "");
        app.setAuditStatus("PENDING");
        memberApplicationRepository.save(app);
        return Result.ok("提交成功，请等待审核");
    }

    @GetMapping("/authenticate/status")
    public Result<Map<String, Object>> getAuthenticateStatus(HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member != null && "admin".equalsIgnoreCase(member.getUsername())) {
            return Result.ok(Map.of("status", "APPROVED", "rejectReason", "", "createTime", ""));
        }
        var app = memberApplicationRepository.findFirstByMemberIdOrderByCreateTimeDesc(memberId);
        if (app.isEmpty()) {
            return Result.ok(Map.of("status", "NONE", "message", "未提交"));
        }
        MemberApplication a = app.get();
        return Result.ok(Map.of(
            "status", a.getAuditStatus(),
            "rejectReason", a.getRejectReason() != null ? a.getRejectReason() : "",
            "createTime", a.getCreateTime() != null ? a.getCreateTime().toString() : ""
        ));
    }

    /** 提现密码是否已设置 */
    @GetMapping("/safe/withdraw-password/status")
    public Result<Map<String, Object>> getWithdrawPasswordStatus(HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        boolean hasSet = memberService.hasWithdrawPassword(memberId);
        return Result.ok(Map.of("hasSet", hasSet));
    }

    /** 首次设置提现密码 */
    @PostMapping("/safe/set-withdraw-password")
    public Result<String> setWithdrawPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        String newPassword = body != null ? body.get("newPassword") : null;
        String pwdErr = (newPassword == null || newPassword.isBlank()) ? "请填写提现密码" : com.vaultpi.common.PasswordPolicy.validate(newPassword);
        if (pwdErr != null) return Result.fail(400, pwdErr);
        if (memberService.hasWithdrawPassword(memberId)) {
            return Result.fail(400, "已设置过提现密码，请使用修改功能");
        }
        memberService.setWithdrawPassword(memberId, newPassword);
        return Result.ok("提现密码已设置");
    }

    /** 修改提现密码（需原密码） */
    @PostMapping("/safe/update-withdraw-password")
    public Result<String> updateWithdrawPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long memberId = SessionUtil.getMemberId(request);
        String oldPassword = body != null ? body.get("oldPassword") : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        if (oldPassword == null || newPassword == null) {
            return Result.fail(400, "请填写原提现密码和新密码");
        }
        String pwdErr = com.vaultpi.common.PasswordPolicy.validate(newPassword);
        if (pwdErr != null) return Result.fail(400, pwdErr);
        memberService.updateWithdrawPassword(memberId, oldPassword, newPassword);
        return Result.ok("提现密码已修改");
    }
}
