package com.vaultpi.user.dto;

import com.vaultpi.user.entity.Member;
import lombok.Data;

import java.time.Instant;

/**
 * 返回给前端的会员信息（不含密码）
 */
@Data
public class MemberDTO {
    private Long id;
    private String username;
    private String email;
    private String status;
    private String role;
    private String nickname;
    private Instant registrationTime;
    private Instant lastLoginTime;

    public static MemberDTO from(Member member) {
        MemberDTO dto = new MemberDTO();
        dto.setId(member.getId());
        dto.setUsername(member.getUsername());
        dto.setEmail(member.getEmail());
        dto.setStatus(member.getStatus());
        dto.setRole(member.getRole() != null ? member.getRole() : "USER");
        dto.setNickname(member.getNickname());
        dto.setRegistrationTime(member.getRegistrationTime());
        dto.setLastLoginTime(member.getLastLoginTime());
        return dto;
    }
}
