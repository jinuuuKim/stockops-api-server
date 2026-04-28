package com.stockops.notification.escalation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for PendingAlert responses.
 *
 * @author StockOps Team
 * @since 2.0
 */
public record PendingAlertDTO(
        Long id,
        String alertType,
        Long centerId,
        Long warehouseId,
        Long sensorId,
        String message,
        String severity,
        PendingAlertStatus status,
        Integer currentLevel,
        Instant acknowledgedAt,
        String acknowledgedBy,
        Instant createdAt,
        Instant updatedAt
) {}

/**
 * DTO for acknowledging a pending alert.
 *
 * @author StockOps Team
 * @since 2.0
 */
record PendingAlertAckRequest(
        String acknowledgedBy
) {}

/**
 * DTO for creating a pending alert from an environment trigger.
 *
 * @author StockOps Team
 * @since 2.0
 */
record PendingAlertCreateRequest(
        String alertType,
        Long centerId,
        Long warehouseId,
        Long sensorId,
        String message,
        String severity
) {}