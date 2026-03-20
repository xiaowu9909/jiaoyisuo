package com.vaultpi.content.repository;

import com.vaultpi.content.entity.SysHelp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysHelpRepository extends JpaRepository<SysHelp, Long> {

    Page<SysHelp> findByStatusAndLangOrderByIsTopAscSortAscCreateTimeDesc(String status, String lang, Pageable pageable);

    Page<SysHelp> findByStatusAndLangAndClassificationOrderByIsTopAscSortAscCreateTimeDesc(
        String status, String lang, String classification, Pageable pageable);

    Page<SysHelp> findAllByOrderBySortAscCreateTimeDesc(Pageable pageable);

    List<SysHelp> findByStatusOrderBySortAscCreateTimeDesc(String status, Pageable pageable);
}
