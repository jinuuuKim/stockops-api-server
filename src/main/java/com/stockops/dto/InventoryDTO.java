package com.stockops.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Inventory response payload with denormalized product, location, and lot details.
 *
 * @param id inventory identifier
 * @param productId product identifier
 * @param productBarcode product barcode
 * @param productName product name
 * @param locationId location identifier
 * @param locationCode location code
 * @param locationName location name
 * @param lotId lot identifier
 * @param lotNumber lot number
 * @param expiryDate lot expiry date
 * @param quantity available quantity
 * @param reservedQuantity reserved quantity
 * @param status inventory status (ACTIVE, RESERVED, QUARANTINE, EXPIRED)
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @author StockOps Team
 * @since 1.0
 */
public record InventoryDTO(
        Long id,
        Long productId,
        String productBarcode,
        String productName,
        Long locationId,
        String locationCode,
        String locationName,
        Long lotId,
        String lotNumber,
        LocalDate expiryDate,
        int quantity,
        int reservedQuantity,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
