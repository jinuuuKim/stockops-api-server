package com.stockops.controller;

import com.stockops.dto.AuditLogDTO;
import com.stockops.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Audit history API controller.
 * Exposes filtered and recent audit log views for privileged users.
 *
 * @author StockOps Team
 * @since 1.0
 * @see AuditLogService
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Returns audit logs with optional filters.
     *
     * @param entityType audited entity type filter
     * @param entityId audited entity identifier filter
     * @param userId performer identifier filter
     * @param startDate start timestamp filter
     * @param endDate end timestamp filter
     * @return matching audit logs
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('AUDIT_LOG_READ')")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @RequestParam(required = false) final String entityType,
            @RequestParam(required = false) final Long entityId,
            @RequestParam(required = false) final Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Instant endDate,
            @PageableDefault(size = 20) final Pageable pageable) {

        return ResponseEntity.ok(auditLogService.searchAuditLogs(entityType, entityId, userId, startDate, endDate, pageable));
    }

    /**
     * Returns the most recent audit logs.
     *
     * @return recent audit logs
     */
    @GetMapping("/recent")
    @PreAuthorize("@permissionChecker.hasPermission('AUDIT_LOG_READ')")
    public ResponseEntity<List<AuditLogDTO>> getRecentAuditLogs() {
        return ResponseEntity.ok(auditLogService.getRecentAuditLogs(50));
    }

    public AuditLogController(final AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

}
