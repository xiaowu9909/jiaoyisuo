package com.vaultpi.content.repository;

import com.vaultpi.content.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findByLangOrderByCreateTimeDesc(String lang, Pageable pageable);

    Page<Announcement> findAllByOrderByCreateTimeDesc(Pageable pageable);
}
