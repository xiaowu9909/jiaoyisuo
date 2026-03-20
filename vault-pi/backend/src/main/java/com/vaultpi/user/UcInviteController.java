package com.vaultpi.user;

import com.vaultpi.common.RequireLogin;
import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RequireLogin
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/uc/invite", ApiPaths.V1 + "/uc/invite" })
public class UcInviteController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final MemberRepository memberRepository;

    public UcInviteController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpSession session) {
        Long memberId = (Long) session.getAttribute(SESSION_MEMBER_ID);
        if (memberId == null) return Result.fail(401, "请先登录");
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return Result.fail(404, "用户不存在");
        long inviteCount = memberRepository.countByParentId(memberId);
        String inviteCode = member.getInviteCode() != null ? member.getInviteCode() : "";
        String baseUrl = "https://example.com"; // 前端可覆盖或从配置读取
        String inviteUrl = baseUrl + "/register?inviteCode=" + inviteCode;
        Map<String, Object> data = new HashMap<>();
        data.put("inviteCode", inviteCode);
        data.put("inviteCount", inviteCount);
        data.put("inviteUrl", inviteUrl);
        return Result.ok(data);
    }

    @GetMapping("/rank")
    public Result<List<Map<String, Object>>> rank(@RequestParam(defaultValue = "20") int limit) {
        List<Member> all = memberRepository.findAll();
        Map<Long, Long> countByParent = new HashMap<>();
        for (Member m : all) {
            if (m.getParentId() != null) {
                countByParent.merge(m.getParentId(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> rankList = countByParent.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(Math.min(50, Math.max(1, limit)))
            .map(e -> {
                Member m = memberRepository.findById(e.getKey()).orElse(null);
                Map<String, Object> item = new HashMap<>();
                item.put("memberId", e.getKey());
                item.put("username", m != null ? m.getUsername() : "?");
                item.put("inviteCount", e.getValue());
                return item;
            })
            .collect(Collectors.toList());
        return Result.ok(rankList);
    }
}
