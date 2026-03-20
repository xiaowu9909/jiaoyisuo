package com.vaultpi.content.repository;

import com.vaultpi.content.entity.SysActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysActivityRepository extends JpaRepository<SysActivity, Long> {
    List<SysActivity> findAllByOrderByCreateTimeDesc();
}
