package com.vaultpi.asset.repository;

import com.vaultpi.asset.entity.MemberDeposit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDepositRepository extends JpaRepository<MemberDeposit, Long> {

    Page<MemberDeposit> findByMemberIdOrderByCreateTimeDesc(Long memberId, Pageable pageable);

    Page<MemberDeposit> findByMemberIdAndCoinIdOrderByCreateTimeDesc(Long memberId, Long coinId, Pageable pageable);

    Page<MemberDeposit> findByCoinIdOrderByCreateTimeDesc(Long coinId, Pageable pageable);
}
