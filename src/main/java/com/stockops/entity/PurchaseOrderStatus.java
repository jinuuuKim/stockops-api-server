package com.stockops.entity;

/**
 * Purchase Order status enum.
 *
 * @author StockOps Team
 * @since 2.0
 */
public enum PurchaseOrderStatus {
    DRAFT,
    REQUESTED,
    ACCEPTED,
    PARTIALLY_ACCEPTED,
    REJECTED,
    CANCELLED,
    SHIPMENT_CREATED,
    INBOUND_PENDING,
    COMPLETED
}
