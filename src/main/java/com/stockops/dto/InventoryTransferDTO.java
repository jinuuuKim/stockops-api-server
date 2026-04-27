package com.stockops.dto;

import com.stockops.entity.InventoryTransferStatus;
import java.time.Instant;

/**
 * Inventory transfer response payload.
 *
 * @param id transfer identifier
 * @param productId product identifier
 * @param lotId lot identifier
 * @param fromLocationId source location identifier
 * @param toLocationId destination location identifier
 * @param quantity transfer quantity
 * @param status workflow status
 * @param requestedBy requesting user identifier
 * @param completedBy completing user identifier
 * @param notes transfer notes
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @author StockOps Team
 * @since 2.0
 */
public record InventoryTransferDTO(
        Long id,
        Long productId,
        Long lotId,
        Long fromLocationId,
        Long toLocationId,
        Integer quantity,
        InventoryTransferStatus status,
        Long requestedBy,
        String requestedByName,
        Long completedBy,
        String completedByName,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
