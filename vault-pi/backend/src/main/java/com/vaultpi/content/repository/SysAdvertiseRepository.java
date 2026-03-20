package com.vaultpi.content.repository;

import com.vaultpi.content.entity.SysAdvertise;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysAdvertiseRepository extends JpaRepository<SysAdvertise, Long> {
    List<SysAdvertise> findAllByOrderByCreateTimeDesc();
    List<SysAdvertise> findByStatusOrderBySortOrderAsc(Integer status);
}
