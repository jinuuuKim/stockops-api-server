package com.stockops.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Inventory transfer creation request payload.
 *
 * @param productId product identifier
 * @param lotId lot identifier (optional)
 * @param fromLocationId source location identifier
 * @param toLocationId destination location identifier
 * @param quantity quantity to transfer
 * @param notes optional notes
 * @author StockOps Team
 * @since 2.0
 */
public record CreateInventoryTransferRequest(
        @NotNull Long productId,
        Long lotId,
        @NotNull Long fromLocationId,
        @NotNull Long toLocationId,
        @NotNull @Positive Integer quantity,
        String notes
) {
}
