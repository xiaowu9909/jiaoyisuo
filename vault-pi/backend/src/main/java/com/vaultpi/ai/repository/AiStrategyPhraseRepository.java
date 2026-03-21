package com.vaultpi.ai.repository;

import com.vaultpi.ai.entity.AiStrategyPhrase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiStrategyPhraseRepository extends JpaRepository<AiStrategyPhrase, Integer> {
    List<AiStrategyPhrase> findByTypeAndStatus(Integer type, Integer status);
    List<AiStrategyPhrase> findByStatus(Integer status);
    List<AiStrategyPhrase> findByType(Integer type);
}
