package com.stockops.service;

import com.stockops.entity.Inventory;
import com.stockops.entity.Warehouse;
import com.stockops.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Center-level inventory aggregation.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CenterInventoryAggregationService {

    private final WarehouseService warehouseService;
    private final InventoryRepository inventoryRepository;

    /**
     * Get aggregated inventory for a center (sum of all warehouses).
     */
    public Map<String, Object> getCenterInventorySummary(Long centerId) {
        List<Warehouse> warehouses = warehouseService.findByCenterId(centerId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("centerId", centerId);
        summary.put("warehouseCount", warehouses.size());

        int totalQuantity = 0;
        int totalItems = 0;

        for (Warehouse warehouse : warehouses) {
            List<Inventory> inventories = inventoryRepository.findByLocationWarehouseId(warehouse.getId());
            for (Inventory inv : inventories) {
                totalQuantity += inv.getQuantity();
                totalItems++;
            }
        }

        summary.put("totalQuantity", totalQuantity);
        summary.put("totalItems", totalItems);

        return summary;
    }
}
