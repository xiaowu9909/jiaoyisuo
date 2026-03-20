package com.vaultpi.market.repository;

import com.vaultpi.market.entity.TopRealCoinsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopRealCoinsSnapshotRepository extends JpaRepository<TopRealCoinsSnapshot, Long> {
    TopRealCoinsSnapshot findTopByOrderByIdDesc();
}

