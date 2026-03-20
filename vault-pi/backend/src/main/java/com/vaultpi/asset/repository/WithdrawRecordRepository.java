package com.vaultpi.asset.repository;

import com.vaultpi.asset.entity.WithdrawRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawRecordRepository extends JpaRepository<WithdrawRecord, Long> {

    Page<WithdrawRecord> findByMemberIdOrderByCreateTimeDesc(Long memberId, Pageable pageable);

    Page<WithdrawRecord> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);
}
