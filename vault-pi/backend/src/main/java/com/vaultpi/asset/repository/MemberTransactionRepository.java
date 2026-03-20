package com.vaultpi.asset.repository;

import com.vaultpi.asset.entity.MemberTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MemberTransactionRepository extends JpaRepository<MemberTransaction, Long> {

    List<MemberTransaction> findByMemberId(Long memberId);

    Page<MemberTransaction> findByMemberIdOrderByCreateTimeDesc(Long memberId, Pageable pageable);

    Page<MemberTransaction> findByMemberIdAndSymbolAndTypeOrderByCreateTimeDesc(Long memberId, String symbol, String type, Pageable pageable);

    Page<MemberTransaction> findByMemberIdAndSymbolOrderByCreateTimeDesc(Long memberId, String symbol, Pageable pageable);

    Page<MemberTransaction> findByMemberIdAndTypeOrderByCreateTimeDesc(Long memberId, String type, Pageable pageable);

    Page<MemberTransaction> findBySymbolAndTypeOrderByCreateTimeDesc(String symbol, String type, Pageable pageable);

    Page<MemberTransaction> findBySymbolOrderByCreateTimeDesc(String symbol, Pageable pageable);

    Page<MemberTransaction> findByTypeOrderByCreateTimeDesc(String type, Pageable pageable);

    @Query("SELECT SUM(t.amount) FROM MemberTransaction t WHERE t.symbol = :symbol AND t.type IN :types AND t.createTime >= :startTime AND t.createTime < :endTime")
    java.math.BigDecimal sumAmountBySymbolAndTypesAndTime(@Param("symbol") String symbol, @Param("types") java.util.List<String> types, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /** 仅统计正常用户（子查询排除内部用户），只计充值/手动充值 或 提现/扣除；JPQL 保证时间参数正确绑定 */
    @Query("SELECT SUM(t.amount) FROM MemberTransaction t WHERE t.memberId IN (SELECT m.id FROM com.vaultpi.user.entity.Member m WHERE m.userType = 'NORMAL') AND t.symbol = :symbol AND t.type IN :types AND t.createTime >= :startTime AND t.createTime < :endTime")
    java.math.BigDecimal sumAmountBySymbolAndTypesAndTimeExcludingInternal(@Param("symbol") String symbol, @Param("types") java.util.List<String> types, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    List<MemberTransaction> findBySymbolAndTypeInAndCreateTimeBetween(String symbol, Collection<String> types, Instant startTime, Instant endTime);

    /** 仅查询正常用户的流水（排除内部用户），用于趋势统计；显式 JOIN member 表按 userType 过滤 */
    @Query("SELECT t FROM MemberTransaction t, com.vaultpi.user.entity.Member m WHERE t.memberId = m.id AND m.userType = 'NORMAL' AND t.symbol = :symbol AND t.type IN :types AND t.createTime >= :startTime AND t.createTime <= :endTime")
    List<MemberTransaction> findBySymbolAndTypeInAndCreateTimeBetweenExcludingInternal(@Param("symbol") String symbol, @Param("types") Collection<String> types, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /** 按会员汇总金额（仅真实会员：role<>ADMIN 且 userType 为 NORMAL 或空），用于财务统计分页 */
    @Query("SELECT t.memberId, SUM(t.amount) FROM MemberTransaction t WHERE t.memberId IN (SELECT m.id FROM com.vaultpi.user.entity.Member m WHERE m.role <> 'ADMIN' AND (m.userType IS NULL OR m.userType = 'NORMAL')) AND t.symbol = :symbol AND t.type IN :types AND t.createTime >= :startTime AND t.createTime < :endTime GROUP BY t.memberId")
    List<Object[]> sumAmountGroupByMemberIdRealMembers(@Param("symbol") String symbol, @Param("types") List<String> types, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
}
