package com.vaultpi.ai.repository;

import com.vaultpi.ai.entity.AiSubscribeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiSubscribeRecordRepository extends JpaRepository<AiSubscribeRecord, Integer> {
    List<AiSubscribeRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
