package com.vaultpi.config;

import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/admin/** 路径需管理员角色，否则 403。
 */
public class AdminRoleInterceptor implements HandlerInterceptor {

    private final MemberRepository memberRepository;

    public AdminRoleInterceptor(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Long memberId = session != null ? (Long) session.getAttribute(RequireLoginInterceptor.SESSION_MEMBER_ID) : null;
        if (memberId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null || !"ADMIN".equals(member.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"需要管理员权限\"}");
            return false;
        }
        return true;
    }
}
