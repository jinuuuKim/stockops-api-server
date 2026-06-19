package com.stockops.notification.lowstock;

import com.stockops.entity.Inventory;
import com.stockops.entity.Product;

/**
 * Single source of truth for the "저재고(low-stock)" predicate.
 *
 * <p>An inventory row is considered low when its <em>available</em> quantity
 * ({@code quantity - reservedQuantity}, floored at 0) is at or below the product's
 * {@code safetyStockQuantity}. Only products with a positive safety-stock threshold are tracked.
 *
 * <p>This is shared by the in-app NotificationBell sync ({@code NotificationService}) and the
 * Teams webhook scan ({@code LowStockWebhookService}) so the two paths can never diverge on what
 * counts as low stock.
 *
 * @author StockOps Team
 * @since 2.5
 */
public final class LowStockRule {

    private LowStockRule() {
    }

    /**
     * Whether a product participates in low-stock tracking (has a positive safety-stock threshold).
     *
     * @param product product (may be null)
     * @return true when the product has a safety-stock threshold greater than zero
     */
    public static boolean isTracked(final Product product) {
        return product != null
                && product.getSafetyStockQuantity() != null
                && product.getSafetyStockQuantity() > 0;
    }

    /**
     * Available quantity for an inventory row: {@code quantity - reservedQuantity}, never negative.
     *
     * @param inventory inventory row
     * @return available quantity (>= 0)
     */
    public static int availableQuantity(final Inventory inventory) {
        return Math.max(0, safeInt(inventory.getQuantity()) - safeInt(inventory.getReservedQuantity()));
    }

    /**
     * Whether the inventory row is low for the given product.
     *
     * @param inventory inventory row
     * @param product   the inventory's product (may be null)
     * @return true when the product is tracked and available quantity is at or below safety stock
     */
    public static boolean isLow(final Inventory inventory, final Product product) {
        return isTracked(product) && availableQuantity(inventory) <= product.getSafetyStockQuantity();
    }

    private static int safeInt(final Integer value) {
        return value == null ? 0 : value;
    }
}
