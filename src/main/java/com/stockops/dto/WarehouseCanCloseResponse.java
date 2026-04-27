package com.stockops.dto;

import java.util.List;

/**
 * Warehouse closure preconditions response.
 *
 * @param canClose true if the warehouse can be closed
 * @param reasons list of human-readable reasons why it cannot be closed
 * @param remainingInventory count of remaining inventory items
 * @param openInbounds count of open inbound drafts targeting this warehouse
 * @param openTransfers count of open transfers involving this warehouse
 * @author StockOps Team
 * @since 2.0
 */
public record WarehouseCanCloseResponse(
    boolean canClose,
    List<String> reasons,
    int remainingInventory,
    int openInbounds,
    int openTransfers
) {
}
