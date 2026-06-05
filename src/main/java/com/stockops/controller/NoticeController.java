package com.stockops.controller;

import com.stockops.dto.NoticeRequest;
import com.stockops.dto.NoticeResponse;
import com.stockops.entity.NoticeType;
import com.stockops.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/active")
    public ResponseEntity<List<NoticeResponse>> getActiveNotices() {
        return ResponseEntity.ok(noticeService.getActiveNotices().stream().map(NoticeResponse::from).toList());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NoticeResponse>> getAllNotices(
            @RequestParam(required = false) final NoticeType type,
            @RequestParam(required = false) final Boolean active) {
        return ResponseEntity.ok(noticeService.getAllNotices(type, active).stream().map(NoticeResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NoticeResponse> createNotice(@Valid @RequestBody final NoticeRequest request) {
        return ResponseEntity.status(201).body(NoticeResponse.from(noticeService.createNotice(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NoticeResponse> updateNotice(@PathVariable final Long id,
                                                       @RequestBody final NoticeRequest request) {
        return ResponseEntity.ok(NoticeResponse.from(noticeService.updateNotice(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotice(@PathVariable final Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.noContent().build();
    }

    public NoticeController(final NoticeService noticeService) {
        this.noticeService = noticeService;
    }

}
