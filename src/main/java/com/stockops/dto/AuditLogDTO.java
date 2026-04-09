package com.stockops.dto;

import java.time.Instant;

/**
 * Audit log response payload.
 * Exposes the audited entity, change values, and performer details for history views.
 *
 * @param id audit log identifier
 * @param entityType audited entity type
 * @param entityId audited entity identifier
 * @param action audit action name
 * @param oldValue previous value snapshot
 * @param newValue new value snapshot
 * @param performedBy performer user identifier
 * @param performedByName performer display name
 * @param performedAt action timestamp
 * @author StockOps Team
 * @since 1.0
 */
public record AuditLogDTO(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String oldValue,
        String newValue,
        Long performedBy,
        String performedByName,
        Instant performedAt
) {
}
