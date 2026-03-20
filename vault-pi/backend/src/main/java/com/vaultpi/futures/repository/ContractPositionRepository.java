package com.vaultpi.futures.repository;

import com.vaultpi.futures.entity.ContractPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractPositionRepository extends JpaRepository<ContractPosition, Long> {
    org.springframework.data.domain.Page<ContractPosition> findAll(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ContractPosition> findByMemberIdOrderByCreateTimeDesc(Long memberId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ContractPosition> findByMemberIdAndSymbolOrderByCreateTimeDesc(Long memberId, String symbol, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ContractPosition> findBySymbolOrderByCreateTimeDesc(String symbol, org.springframework.data.domain.Pageable pageable);
    List<ContractPosition> findByMemberIdAndStatus(Long memberId, String status);
    List<ContractPosition> findByStatus(String status);
}
