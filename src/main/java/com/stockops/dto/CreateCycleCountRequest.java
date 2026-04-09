package com.stockops.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Cycle count creation request payload.
 *
 * @param countDate date assigned to the count
 * @param locationId location to count
 * @param inventoryIds inventory rows included in the count
 * @author StockOps Team
 * @since 1.0
 */
public record CreateCycleCountRequest(
        @NotNull LocalDate countDate,
        @NotNull Long locationId,
        @NotEmpty List<@NotNull Long> inventoryIds
) {
}
