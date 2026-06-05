package com.stockops.repository;

import com.stockops.entity.Notice;
import com.stockops.entity.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findByActiveTrueOrderByCreatedAtDesc();
    Page<Notice> findByType(NoticeType type, Pageable pageable);
    List<Notice> findByActiveTrueAndTypeOrderByCreatedAtDesc(NoticeType type);
    List<Notice> findAllByOrderByCreatedAtDesc();
    List<Notice> findByActiveOrderByCreatedAtDesc(Boolean active);
    List<Notice> findByTypeOrderByCreatedAtDesc(NoticeType type);
    List<Notice> findByTypeAndActiveOrderByCreatedAtDesc(NoticeType type, Boolean active);
}
