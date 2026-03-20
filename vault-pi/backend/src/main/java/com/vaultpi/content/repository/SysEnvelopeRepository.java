package com.vaultpi.content.repository;

import com.vaultpi.content.entity.SysEnvelope;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysEnvelopeRepository extends JpaRepository<SysEnvelope, Long> {
    List<SysEnvelope> findAllByOrderByCreateTimeDesc();
}
