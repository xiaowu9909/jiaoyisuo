package com.vaultpi.user.repository;

import com.vaultpi.user.entity.MemberApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberApplicationRepository extends JpaRepository<MemberApplication, Long> {

    List<MemberApplication> findByMemberIdOrderByCreateTimeDesc(Long memberId);

    Optional<MemberApplication> findFirstByMemberIdOrderByCreateTimeDesc(Long memberId);

    Page<MemberApplication> findByAuditStatusOrderByCreateTimeDesc(String auditStatus, Pageable pageable);
}
