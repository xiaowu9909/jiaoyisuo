package com.vaultpi.ai.repository;

import com.vaultpi.ai.entity.AiPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiPlanRepository extends JpaRepository<AiPlan, Integer> {
    List<AiPlan> findByStatus(Integer status);
}
