package com.stockops.service;

import com.stockops.dto.AuditLogDTO;
import com.stockops.entity.AuditLog;
import com.stockops.entity.User;
import com.stockops.repository.AuditLogRepository;
import com.stockops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Provides audit history queries for administrative review.
 * Supports entity, user, date-range, and recent activity lookups.
 *
 * @author StockOps Team
 * @since 1.0
 * @see AuditLogRepository
 * @see UserRepository
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves audit logs for a specific entity.
     *
     * @param entityType audited entity type
     * @param entityId audited entity identifier
     * @return matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLogDTO> getAuditLogs(final String entityType, final Long entityId) {
        return auditLogRepository.findAuditLogs(entityType, entityId, null, null, null, Pageable.unpaged())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Retrieves the most recent audit logs.
     *
     * @param limit maximum number of logs to return
     * @return recent audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLogDTO> getRecentAuditLogs(final int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return auditLogRepository.findRecentAuditLogs(PageRequest.of(0, limit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Retrieves audit logs created by a specific user.
     *
     * @param userId performer identifier
     * @return matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLogDTO> getAuditLogsByUser(final Long userId) {
        return auditLogRepository.findAuditLogs(null, null, userId, null, null, Pageable.unpaged())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Retrieves audit logs within a date range.
     *
     * @param start start timestamp
     * @param end end timestamp
     * @return matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLogDTO> getAuditLogsByDateRange(final Instant start, final Instant end) {
        return auditLogRepository.findAuditLogs(null, null, null, start, end, Pageable.unpaged())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Retrieves audit logs using all supported filters.
     *
     * @param entityType audited entity type
     * @param entityId audited entity identifier
     * @param userId performer identifier
     * @param start start timestamp
     * @param end end timestamp
     * @return matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLogDTO> searchAuditLogs(final String entityType,
                                             final Long entityId,
                                             final Long userId,
                                             final Instant start,
                                             final Instant end) {
        return auditLogRepository.findAuditLogs(entityType, entityId, userId, start, end, Pageable.unpaged())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AuditLogDTO toDto(final AuditLog auditLog) {
        final User performer = auditLog.getPerformedBy() == null ? null : userRepository.findById(auditLog.getPerformedBy()).orElse(null);
        return new AuditLogDTO(
                auditLog.getId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getAction(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getPerformedBy(),
                performer == null ? null : performer.getName(),
                auditLog.getPerformedAt());
    }
}
