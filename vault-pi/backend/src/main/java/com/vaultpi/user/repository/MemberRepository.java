package com.vaultpi.user.repository;

import com.vaultpi.user.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByRole(String role);

    Optional<Member> findByUsername(String username);

    Optional<Member> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUid(Integer uid);

    Optional<Member> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    long countByParentId(Long parentId);

    @Query("SELECT m FROM Member m WHERE m.role <> 'ADMIN' " +
           "AND (:status IS NULL OR :status = '' OR m.status = :status) " +
           "AND (:kw IS NULL OR :kw = '' OR LOWER(m.username) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.email,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.phone,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.realName,'')) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Member> findMembers(@Param("kw") String kw, @Param("status") String status, Pageable pageable);

    List<Member> findByParentIdIn(List<Long> parentIds);

    @Query("SELECT m FROM Member m WHERE m.id IN :ids AND m.role <> 'ADMIN' " +
           "AND (:status IS NULL OR :status = '' OR m.status = :status) " +
           "AND (:kw IS NULL OR :kw = '' OR LOWER(m.username) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.email,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.phone,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.realName,'')) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Member> findMembersByIdIn(@Param("ids") List<Long> ids, @Param("kw") String kw, @Param("status") String status, Pageable pageable);

    /** 真实会员（非管理员且非内部）：用于财务统计等，支持按用户名/邮箱/手机/实名/UID 搜索 */
    @Query("SELECT m FROM Member m WHERE m.role <> 'ADMIN' AND (m.userType IS NULL OR m.userType = 'NORMAL') " +
           "AND (:status IS NULL OR :status = '' OR m.status = :status) " +
           "AND (:kw IS NULL OR :kw = '' OR LOWER(m.username) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.email,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.phone,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(COALESCE(m.realName,'')) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR (m.uid IS NOT NULL AND CAST(m.uid AS string) LIKE CONCAT('%', :kw, '%')))")
    Page<Member> findRealMembers(@Param("kw") String kw, @Param("status") String status, Pageable pageable);
}
