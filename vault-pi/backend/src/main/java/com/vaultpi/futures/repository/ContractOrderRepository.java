package com.vaultpi.futures.repository;

import com.vaultpi.futures.entity.ContractOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractOrderRepository extends JpaRepository<ContractOrder, String> {
    org.springframework.data.domain.Page<ContractOrder> findByMemberIdOrderByCreateTimeDesc(Long memberId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ContractOrder> findByMemberIdAndSymbolOrderByCreateTimeDesc(Long memberId, String symbol, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ContractOrder> findBySymbolOrderByCreateTimeDesc(String symbol, org.springframework.data.domain.Pageable pageable);
    List<ContractOrder> findByStatus(String status);
}
