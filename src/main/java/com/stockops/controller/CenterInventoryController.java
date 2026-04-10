package com.stockops.controller;

import com.stockops.service.CenterInventoryAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Center inventory aggregation.
 *
 * @author StockOps Team
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/centers")
@RequiredArgsConstructor
public class CenterInventoryController {

    private final CenterInventoryAggregationService centerInventoryService;

    @GetMapping("/{centerId}/inventory")
    public Map<String, Object> getCenterInventory(@PathVariable Long centerId) {
        return centerInventoryService.getCenterInventorySummary(centerId);
    }
}
