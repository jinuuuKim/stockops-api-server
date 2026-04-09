package com.stockops.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for recording a cycle count result.
 *
 * @param itemId cycle count item identifier
 * @param actualQuantity counted quantity
 * @param notes operator notes
 * @author StockOps Team
 * @since 1.0
 */
public record RecordCountRequest(
        @NotNull Long itemId,
        @NotNull Integer actualQuantity,
        String notes
) {
}
