package com.stockops.dto;

/**
 * Dashboard summary payload.
 *
 * @param totalProducts total registered products
 * @param totalInventoryQuantity total available inventory quantity
 * @param todayInboundCount inbound count for today
 * @param todayOutboundCount outbound count for today
 * @param lowStockCount low-stock inventory row count
 * @param pendingCycleCounts pending cycle count items
 * @param criticalExpiryCount active critical expiry alert count
 * @param warningExpiryCount active warning expiry alert count
 * @param recentTransactionCount inventory transactions from the last 7 days
 * @author StockOps Team
 * @since 1.0
 */
public record DashboardSummaryDTO(
        long totalProducts,
        long totalInventoryQuantity,
        long todayInboundCount,
        long todayOutboundCount,
        long lowStockCount,
        long pendingCycleCounts,
        long criticalExpiryCount,
        long warningExpiryCount,
        long recentTransactionCount) {
}
