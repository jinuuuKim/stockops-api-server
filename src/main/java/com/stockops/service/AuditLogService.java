package com.stockops.service;

import com.stockops.dto.AuditLogDTO;
import com.stockops.entity.AuditLog;
import com.stockops.entity.User;
import com.stockops.repository.AuditLogRepository;
import com.stockops.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
        return searchAuditLogs(entityType, entityId, null, null, null);
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
        return searchAuditLogs(null, null, userId, null, null);
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
        return searchAuditLogs(null, null, null, start, end);
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
        return searchAuditLogs(entityType, entityId, userId, start, end,
                        PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "performedAt", "id")))
                .stream()
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> searchAuditLogs(final String entityType,
                                             final Long entityId,
                                             final Long userId,
                                             final Instant start,
                                             final Instant end,
                                             final Pageable pageable) {
        final Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "performedAt", "id"));
        return auditLogRepository.findAll(
                        auditLogSpecification(entityType, entityId, userId, start, end),
                        effectivePageable)
                .map(this::toDto);
    }

    private Specification<AuditLog> auditLogSpecification(final String entityType,
                                                          final Long entityId,
                                                          final Long userId,
                                                          final Instant start,
                                                          final Instant end) {
        return (root, query, criteriaBuilder) -> {
            final List<Predicate> predicates = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(criteriaBuilder.equal(root.get("entityId"), entityId));
            }
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("performedBy"), userId));
            }
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("performedAt"), start));
            }
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("performedAt"), end));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditLogDTO toDto(final AuditLog auditLog) {
        final User performer = auditLog.getPerformedBy() == null ? null : userRepository.findById(auditLog.getPerformedBy()).orElse(null);
        return new AuditLogDTO(
                auditLog.getId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getTargetIdentifier(),
                auditLog.getAction(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getPerformedBy(),
                performer == null ? null : performer.getName(),
                auditLog.getPerformedByEmail(),
                auditLog.getPerformedAt());
    }

    public AuditLogService(final AuditLogRepository auditLogRepository, final UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }
}
