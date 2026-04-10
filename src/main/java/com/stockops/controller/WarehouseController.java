package com.stockops.controller;

import com.stockops.entity.Warehouse;
import com.stockops.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Warehouse management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public List<Warehouse> getAllWarehouses() {
        return warehouseService.findAll();
    }

    @GetMapping("/center/{centerId}")
    public List<Warehouse> getWarehousesByCenter(@PathVariable Long centerId) {
        return warehouseService.findByCenterId(centerId);
    }

    @GetMapping("/center/{centerId}/active")
    public List<Warehouse> getActiveWarehousesByCenter(@PathVariable Long centerId) {
        return warehouseService.findActiveByCenterId(centerId);
    }

    @GetMapping("/{id}")
    public Warehouse getWarehouseById(@PathVariable Long id) {
        return warehouseService.findById(id);
    }

    @PostMapping("/center/{centerId}")
    public Warehouse createWarehouse(@PathVariable Long centerId, @RequestBody Warehouse warehouse) {
        return warehouseService.create(centerId, warehouse);
    }

    @PutMapping("/{id}")
    public Warehouse updateWarehouse(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        return warehouseService.update(id, warehouse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
