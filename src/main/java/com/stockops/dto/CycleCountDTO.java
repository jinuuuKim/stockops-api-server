package com.stockops.dto;

import com.stockops.entity.CycleCountStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Cycle count header response payload.
 *
 * @param id cycle count identifier
 * @param countDate planned or executed count date
 * @param status workflow status
 * @param locationId location being counted
 * @param createdBy creator user id
 * @param completedBy completing user id
 * @param createdAt creation timestamp
 * @param completedAt completion timestamp
 * @param items cycle count detail rows
 * @author StockOps Team
 * @since 1.0
 */
public record CycleCountDTO(
        Long id,
        LocalDate countDate,
        CycleCountStatus status,
        Long locationId,
        Long createdBy,
        Long completedBy,
        Instant createdAt,
        Instant completedAt,
        List<CycleCountItemDTO> items
) {
}
