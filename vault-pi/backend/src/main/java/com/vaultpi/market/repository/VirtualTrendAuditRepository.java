package com.vaultpi.market.repository;

import com.vaultpi.market.entity.VirtualTrendAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualTrendAuditRepository extends JpaRepository<VirtualTrendAudit, Long> {

    Page<VirtualTrendAudit> findBySymbolOrderByOperationTimeDesc(String symbol, Pageable pageable);

    Page<VirtualTrendAudit> findByAdminIdOrderByOperationTimeDesc(Long adminId, Pageable pageable);

    Page<VirtualTrendAudit> findBySymbolAndAdminIdOrderByOperationTimeDesc(String symbol, Long adminId, Pageable pageable);

    Page<VirtualTrendAudit> findAllByOrderByOperationTimeDesc(Pageable pageable);
}
