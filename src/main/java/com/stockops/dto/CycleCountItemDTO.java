package com.stockops.dto;

import java.time.Instant;

/**
 * Cycle count item response payload.
 *
 * @param id cycle count item identifier
 * @param cycleCountId parent cycle count identifier
 * @param inventoryId inventory row identifier
 * @param expectedQuantity system quantity captured for counting
 * @param actualQuantity physically counted quantity
 * @param variance difference between actual and expected quantity
 * @param countedBy operator who recorded the final count
 * @param countedAt time the final count was recorded
 * @param notes operator notes for the variance or observation
 * @author StockOps Team
 * @since 1.0
 */
public record CycleCountItemDTO(
        Long id,
        Long cycleCountId,
        Long inventoryId,
        int expectedQuantity,
        Integer actualQuantity,
        Integer variance,
        Long countedBy,
        Instant countedAt,
        String notes
) {
}
