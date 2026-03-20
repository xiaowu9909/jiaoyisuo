package com.vaultpi.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 会员用户（与旧项目 Member 对应，仅保留邮箱/用户名鉴权相关字段）
 */
@Entity
@Table(name = "member")
@Data
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外展示的 6 位唯一 UID（100000-999999） */
    @Column(unique = true)
    private Integer uid;

    /** NORMAL=正常用户（参与统计）, INTERNAL=内部用户（不参与统计），老数据为空时按 NORMAL 处理 */
    @Column(name = "user_type", length = 16)
    private String userType = "NORMAL";

    /** 登录密码（BCrypt 加密） */
    @NotBlank
    @Column(nullable = false)
    private String password;

    /** 提现密码（BCrypt 加密，可选；未设置时提现不校验） */
    @Column(name = "withdraw_password", length = 128)
    private String withdrawPassword;

    @NotBlank
    @Column(unique = true, nullable = false, length = 64)
    private String username;

    @Column(unique = true, length = 128)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(length = 64)
    private String nickname;

    @Column(length = 64)
    private String realName;

    @Column(length = 32)
    private String idCard;

    /** 邀请码（用于邀请链接） */
    @Column(length = 32)
    private String inviteCode;

    /** 邀请人会员 ID */
    @Column(name = "parent_id")
    private Long parentId;

    /** 正常 / 禁用 */
    @Column(nullable = false, length = 16)
    private String status = "NORMAL";

    /** USER=普通用户, ADMIN=管理员 */
    @Column(nullable = false, length = 16)
    private String role = "USER";

    /** 二级管理员显示名称（仅 role=ADMIN 时使用） */
    @Column(name = "admin_display_name", length = 64)
    private String adminDisplayName;

    /** 二级管理员可见权限，JSON 数组字符串，如 ["member","finance"]；空或 null 表示全部权限 */
    @Column(name = "admin_permissions", length = 2000)
    private String adminPermissions;

    /** 管理员角色：KEFU=客服, ZHUGUAN=主管（数据范围仅自己推广列表）, null=全量 */
    @Column(name = "admin_role", length = 16)
    private String adminRole;

    /** 绑定的前台会员 ID（仅 role=ADMIN 时有效，用于区分部门/归属） */
    @Column(name = "bound_member_id")
    private Long boundMemberId;

    @Column(nullable = false, updatable = false)
    private Instant registrationTime;

    private Instant lastLoginTime;

    /** 会员等级 0-6，默认 0；根据累计充值自动晋升 */
    @Column(name = "vip_level")
    private Integer vipLevel = 0;

    /** 累计充值金额（仅统计结算币如 USDT，用于 VIP 晋级） */
    @Column(name = "total_recharge", precision = 24, scale = 8)
    private BigDecimal totalRecharge = BigDecimal.ZERO;

    @PrePersist
    public void prePersist() {
        if (registrationTime == null) {
            registrationTime = Instant.now();
        }
    }
}
