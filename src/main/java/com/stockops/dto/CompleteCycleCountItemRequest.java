package com.stockops.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Final count input for a single cycle count item.
 *
 * @param itemId cycle count item identifier
 * @param actualQuantity physically counted quantity
 * @param notes optional notes recorded during counting
 * @author StockOps Team
 * @since 1.0
 */
public record CompleteCycleCountItemRequest(
        @NotNull Long itemId,
        @NotNull @PositiveOrZero Integer actualQuantity,
        String notes
) {
}
