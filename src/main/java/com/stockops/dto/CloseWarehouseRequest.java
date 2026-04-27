package com.stockops.dto;

/**
 * Warehouse closure request payload.
 *
 * @param reason closure reason
 * @author StockOps Team
 * @since 2.0
 */
public record CloseWarehouseRequest(String reason) {
}
