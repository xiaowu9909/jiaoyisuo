package com.vaultpi.user.repository;

import com.vaultpi.user.entity.VipLevelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VipLevelConfigRepository extends JpaRepository<VipLevelConfig, Long> {

    List<VipLevelConfig> findAllByOrderByLevelAsc();

    java.util.Optional<VipLevelConfig> findByLevel(Integer level);
}
