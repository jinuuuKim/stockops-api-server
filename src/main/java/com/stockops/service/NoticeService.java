package com.stockops.service;

import com.stockops.dto.NoticeRequest;
import com.stockops.entity.Notice;
import com.stockops.entity.NoticeType;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.NoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public List<Notice> getActiveNotices() {
        return noticeRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Notice> getNoticesByType(NoticeType type) {
        return noticeRepository.findByActiveTrueAndTypeOrderByCreatedAtDesc(type);
    }

    @Transactional
    public Notice createNotice(final NoticeRequest request) {
        Notice notice = new Notice();
        notice.setTitle(request.title());
        notice.setContent(request.content());
        notice.setType(request.type() != null ? request.type() : NoticeType.SYSTEM);
        notice.setCreatedBy(request.createdBy());
        notice.setActive(request.active() != null ? request.active() : true);
        notice.setNoticeAt(request.noticeAt());
        return noticeRepository.save(notice);
    }

    @Transactional
    public Notice updateNotice(final Long id, final NoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        if (request.title() != null && !request.title().isBlank()) notice.setTitle(request.title());
        if (request.content() != null) notice.setContent(request.content());
        if (request.type() != null) notice.setType(request.type());
        if (request.active() != null) notice.setActive(request.active());
        if (request.noticeAt() != null) notice.setNoticeAt(request.noticeAt());
        return noticeRepository.save(notice);
    }

    @Transactional
    public void deleteNotice(final Long id) {
        Notice notice = noticeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        noticeRepository.delete(notice);
    }

    @Transactional(readOnly = true)
    public List<Notice> getAllNotices(final NoticeType type, final Boolean active) {
        if (type != null && active != null) {
            return noticeRepository.findByTypeAndActiveOrderByCreatedAtDesc(type, active);
        }
        if (type != null) {
            return noticeRepository.findByTypeOrderByCreatedAtDesc(type);
        }
        if (active != null) {
            return noticeRepository.findByActiveOrderByCreatedAtDesc(active);
        }
        return noticeRepository.findAllByOrderByCreatedAtDesc();
    }

    public NoticeService(final NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

}
