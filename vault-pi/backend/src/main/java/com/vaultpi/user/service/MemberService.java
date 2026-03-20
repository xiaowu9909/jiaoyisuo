package com.vaultpi.user.service;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.asset.repository.MemberWalletRepository;
import com.vaultpi.common.PasswordPolicy;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.entity.VipLevelConfig;
import com.vaultpi.user.repository.MemberRepository;
import com.vaultpi.user.repository.VipLevelConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MemberService {

    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String LOGIN_LOCK_PREFIX = "login_lock:";
    private static final int FAIL_WINDOW_SECONDS = 300;   // 5 分钟内
    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_DURATION_SECONDS = 900;  // 锁定 15 分钟

    private final MemberRepository memberRepository;
    private final CoinRepository coinRepository;
    private final MemberWalletRepository memberWalletRepository;
    private final VipLevelConfigRepository vipLevelConfigRepository;
    private final StringRedisTemplate redisTemplate; // 无 Redis 时为 null，登录限次/锁定不可用
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /** 仅统计该币种作为“累计充值”用于 VIP 晋级（与财务统计单位一致） */
    public static final String VIP_RECHARGE_UNIT = "USDT";

    public MemberService(MemberRepository memberRepository,
                         CoinRepository coinRepository,
                         MemberWalletRepository memberWalletRepository,
                         VipLevelConfigRepository vipLevelConfigRepository,
                         @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.memberRepository = memberRepository;
        this.coinRepository = coinRepository;
        this.memberWalletRepository = memberWalletRepository;
        this.vipLevelConfigRepository = vipLevelConfigRepository;
        this.redisTemplate = redisTemplate;
    }

    /** 累计充值（USDT）并按配置重新计算会员等级 */
    @Transactional(rollbackFor = Exception.class)
    public void addRechargeAndUpdateVip(Member member, BigDecimal amountUsdt) {
        if (member == null || member.getId() == null || amountUsdt == null || amountUsdt.compareTo(BigDecimal.ZERO) <= 0) return;
        member = memberRepository.findById(member.getId()).orElse(null);
        if (member == null) return;
        BigDecimal total = member.getTotalRecharge() != null ? member.getTotalRecharge() : BigDecimal.ZERO;
        member.setTotalRecharge(total.add(amountUsdt));
        recalcVipLevel(member);
        memberRepository.save(member);
    }

    /** 根据 totalRecharge 从配置表计算并设置 vipLevel */
    public void recalcVipLevel(Member member) {
        if (member == null) return;
        BigDecimal total = member.getTotalRecharge() != null ? member.getTotalRecharge() : BigDecimal.ZERO;
        List<VipLevelConfig> configs = vipLevelConfigRepository.findAllByOrderByLevelAsc();
        int level = 0;
        for (VipLevelConfig c : configs) {
            if (c.getRechargeThreshold() != null && total.compareTo(c.getRechargeThreshold()) >= 0) {
                level = c.getLevel() != null ? c.getLevel() : 0;
            }
        }
        member.setVipLevel(level);
    }

    /** 获取会员当前等级允许的最大杠杆倍数 */
    public int getMaxLeverage(Long memberId) {
        if (memberId == null) return 5;
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return 5;
        Integer level = member.getVipLevel() != null ? member.getVipLevel() : 0;
        return vipLevelConfigRepository.findByLevel(level)
            .map(VipLevelConfig::getLeverageMultiplier)
            .orElse(5);
    }

    @Transactional(rollbackFor = Exception.class)
    public Member registerByEmail(String email, String rawPassword) {
        return registerByEmail(email, rawPassword, null);
    }

    /** C 端注册：邮箱 + 用户名 + 密码 + 邀请码（选填） */
    @Transactional(rollbackFor = Exception.class)
    public Member registerWithUsername(String email, String username, String rawPassword, String inviteCode) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String pwdErr = PasswordPolicy.validateNotSameAsIdentity(rawPassword, username, email);
        if (pwdErr != null) throw new IllegalArgumentException(pwdErr);
        String emailNorm = email.trim().toLowerCase();
        String uname = username.trim();
        if (memberRepository.existsByEmail(emailNorm)) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        if (memberRepository.existsByUsername(uname)) {
            throw new IllegalArgumentException("该用户名已被使用");
        }
        Member member = new Member();
        member.setEmail(emailNorm);
        member.setUsername(uname);
        member.setPassword(passwordEncoder.encode(rawPassword));
        member.setStatus("NORMAL");
        member.setUserType("NORMAL");
        member.setUid(generateUniqueUid());
        member.setRegistrationTime(Instant.now());
        member.setInviteCode("INV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        if (inviteCode != null && !inviteCode.isBlank()) {
            memberRepository.findByInviteCode(inviteCode.trim().toUpperCase()).ifPresent(inviter -> {
                member.setParentId(inviter.getId());
            });
        }
        Member saved = memberRepository.save(member);
        createWalletsForMember(saved.getId());
        return saved;
    }

    /** 为会员创建所有启用币种的钱包（余额、冻结均为 0） */
    private void createWalletsForMember(Long memberId) {
        List<Coin> coins = coinRepository.findByEnableTrue();
        for (Coin coin : coins) {
            MemberWallet w = new MemberWallet();
            w.setMemberId(memberId);
            w.setCoinId(coin.getId());
            w.setBalance(BigDecimal.ZERO);
            w.setFrozenBalance(BigDecimal.ZERO);
            memberWalletRepository.save(w);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Member registerByEmail(String email, String rawPassword, String inviteCode) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        String pwdErr = PasswordPolicy.validate(rawPassword);
        if (pwdErr != null) throw new IllegalArgumentException(pwdErr);
        String emailNorm = email.trim().toLowerCase();
        if (memberRepository.existsByEmail(emailNorm)) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        String username = "u_" + emailNorm.replace("@", "_") + "_" + UUID.randomUUID().toString().substring(0, 8);
        Member member = new Member();
        member.setEmail(emailNorm);
        member.setUsername(username);
        member.setPassword(passwordEncoder.encode(rawPassword));
        member.setStatus("NORMAL");
        member.setUserType("NORMAL");
        member.setUid(generateUniqueUid());
        member.setRegistrationTime(Instant.now());
        member.setInviteCode("INV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        if (inviteCode != null && !inviteCode.isBlank()) {
            memberRepository.findByInviteCode(inviteCode.trim().toUpperCase()).ifPresent(inviter -> {
                member.setParentId(inviter.getId());
            });
        }
        Member saved = memberRepository.save(member);
        createWalletsForMember(saved.getId());
        return saved;
    }

    public Member login(String usernameOrEmail, String rawPassword) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || rawPassword == null) {
            throw new IllegalArgumentException("请填写账号和密码");
        }
        String input = usernameOrEmail.trim();
        String lockKey = LOGIN_LOCK_PREFIX + input.toLowerCase();
        String failKey = LOGIN_FAIL_PREFIX + input.toLowerCase();

        if (redisTemplate != null) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
                Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
                throw new IllegalArgumentException("账户已锁定，请 " + (ttl != null && ttl > 0 ? (ttl / 60 + 1) + " 分钟" : "15 分钟") + " 后再试或联系管理员");
            }
        }

        Optional<Member> byEmail = memberRepository.findByEmail(input);
        Optional<Member> byUsername = memberRepository.findByUsername(input);
        Member member = byEmail.or(() -> byUsername).orElse(null);

        if (member != null && passwordEncoder.matches(rawPassword, member.getPassword())) {
            if (redisTemplate != null) {
                redisTemplate.delete(failKey);
                redisTemplate.delete(lockKey);
            }
            return processLoginSuccess(member);
        }

        if (redisTemplate != null) {
            long newCount = redisTemplate.opsForValue().increment(failKey, 1);
            redisTemplate.expire(failKey, Duration.ofSeconds(FAIL_WINDOW_SECONDS));
            if (newCount >= MAX_FAIL_COUNT) {
                redisTemplate.opsForValue().set(lockKey, "1", Duration.ofSeconds(LOCK_DURATION_SECONDS));
                throw new IllegalArgumentException("密码错误次数过多，账户已锁定 15 分钟，请稍后再试或联系管理员");
            }
            int remaining = (int) (MAX_FAIL_COUNT - newCount);
            throw new IllegalArgumentException("账号或密码错误，剩余尝试次数 " + remaining);
        }
        throw new IllegalArgumentException("账号或密码错误");
    }

    private Member processLoginSuccess(Member member) {
        if (!"NORMAL".equals(member.getStatus())) {
            throw new IllegalArgumentException("该帐号已被禁用，请联系客服");
        }
        member.setLastLoginTime(Instant.now());
        memberRepository.save(member);
        return member;
    }

    /** 是否为默认弱密码（如 admin123），用于强制管理员首次修改密码 */
    public boolean isDefaultWeakPassword(Member member) {
        if (member == null || member.getPassword() == null) return false;
        return passwordEncoder.matches("admin123", member.getPassword());
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 邀请码字符集：大写字母 + 数字（6 位） */
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** 生成 6 位大写字母+数字的唯一邀请码（供内部用户等使用） */
    public String generateUniqueInviteCode6() {
        for (int i = 0; i < 50; i++) {
            StringBuilder sb = new StringBuilder(6);
            for (int j = 0; j < 6; j++) {
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!memberRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("无法生成唯一邀请码，请稍后重试");
    }

    /** 若为内部用户且邀请码为空或非 6 位格式，则分配并保存 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureInternalInviteCode(Member member) {
        if (member == null || member.getId() == null) return;
        if (!"INTERNAL".equals(member.getUserType())) return;
        String code = member.getInviteCode();
        if (code != null && code.length() == 6 && code.matches("[A-Z0-9]{6}")) return;
        member.setInviteCode(generateUniqueInviteCode6());
        memberRepository.save(member);
    }

    /** 生成 100000～999999 范围内唯一的 6 位 UID */
    public Integer generateUniqueUid() {
        for (int i = 0; i < 50; i++) {
            int uid = 100_000 + RANDOM.nextInt(900_000);
            if (!memberRepository.existsByUid(uid)) {
                return uid;
            }
        }
        throw new IllegalStateException("无法生成唯一 UID，请稍后重试");
    }

    /** 若会员尚无 UID 则分配并保存（兼容老数据） */
    @Transactional(rollbackFor = Exception.class)
    public void ensureUid(Member member) {
        if (member == null || member.getId() == null) return;
        if (member.getUid() != null) return;
        member.setUid(generateUniqueUid());
        if (member.getUserType() == null || member.getUserType().isEmpty()) {
            member.setUserType("NORMAL");
        }
        memberRepository.save(member);
    }

    @Transactional(rollbackFor = Exception.class)
    public Member createMemberByAdmin(String username, String rawPassword, String userType, String referrerInviteCode) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String pwdErr = PasswordPolicy.validate(rawPassword);
        if (pwdErr != null) throw new IllegalArgumentException(pwdErr);
        String uname = username.trim();
        if (memberRepository.existsByUsername(uname)) {
            throw new IllegalArgumentException("该用户名已存在");
        }
        String type = "INTERNAL".equalsIgnoreCase(userType) ? "INTERNAL" : "NORMAL";
        Member member = new Member();
        member.setUsername(uname);
        member.setEmail(uname + "@internal.local");
        member.setPassword(passwordEncoder.encode(rawPassword));
        member.setStatus("NORMAL");
        member.setRole("USER");
        member.setUserType(type);
        member.setUid(generateUniqueUid());
        member.setRegistrationTime(Instant.now());
        if ("INTERNAL".equals(type)) {
            member.setInviteCode(generateUniqueInviteCode6());
        } else {
            member.setInviteCode("INV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        }
        if (referrerInviteCode != null && !referrerInviteCode.isBlank()) {
            memberRepository.findByInviteCode(referrerInviteCode.trim().toUpperCase())
                .ifPresent(inviter -> member.setParentId(inviter.getId()));
        }
        Member saved = memberRepository.save(member);
        createWalletsForMember(saved.getId());
        return saved;
    }

    public Optional<Member> findById(Long id) {
        return memberRepository.findById(id);
    }

    @Transactional
    public void updatePassword(Long memberId, String rawPassword) {
        String pwdErr = PasswordPolicy.validate(rawPassword);
        if (pwdErr != null) throw new IllegalArgumentException(pwdErr);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        member.setPassword(passwordEncoder.encode(rawPassword));
        memberRepository.save(member);
    }

    /** 是否已完成实名认证（B 端审核通过后会将 realName 写入 Member）；用户名为 admin 的账户默认视为已实名 */
    public boolean isRealNameVerified(Long memberId) {
        if (memberId == null) return false;
        return memberRepository.findById(memberId)
            .map(m -> "admin".equalsIgnoreCase(m.getUsername()) || (m.getRealName() != null && !m.getRealName().isBlank()))
            .orElse(false);
    }

    /** 是否已设置提现密码 */
    public boolean hasWithdrawPassword(Long memberId) {
        if (memberId == null) return false;
        return memberRepository.findById(memberId)
            .map(m -> m.getWithdrawPassword() != null && !m.getWithdrawPassword().isEmpty())
            .orElse(false);
    }

    /** 设置/修改提现密码（需符合密码策略） */
    @Transactional
    public void setWithdrawPassword(Long memberId, String rawPassword) {
        String pwdErr = PasswordPolicy.validate(rawPassword);
        if (pwdErr != null) throw new IllegalArgumentException("提现密码：" + pwdErr);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        member.setWithdrawPassword(passwordEncoder.encode(rawPassword));
        memberRepository.save(member);
    }

    /** 修改提现密码（需验证原密码） */
    @Transactional
    public void updateWithdrawPassword(Long memberId, String oldPassword, String newPassword) {
        String pwdErr = PasswordPolicy.validate(newPassword);
        if (pwdErr != null) throw new IllegalArgumentException("新提现密码：" + pwdErr);
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String stored = member.getWithdrawPassword();
        if (stored == null || stored.isEmpty()) {
            throw new IllegalArgumentException("未设置过提现密码，请直接设置");
        }
        if (!passwordEncoder.matches(oldPassword, stored)) {
            throw new IllegalArgumentException("原提现密码错误");
        }
        member.setWithdrawPassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);
    }

    /** 验证提现密码，正确返回 true */
    public boolean checkWithdrawPassword(Long memberId, String rawPassword) {
        if (memberId == null || rawPassword == null) return false;
        return memberRepository.findById(memberId)
            .map(m -> {
                String stored = m.getWithdrawPassword();
                return stored != null && !stored.isEmpty() && passwordEncoder.matches(rawPassword, stored);
            })
            .orElse(false);
    }
}
