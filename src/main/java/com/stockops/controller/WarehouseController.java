package com.stockops.controller;

import com.stockops.dto.CloseWarehouseRequest;
import com.stockops.dto.WarehouseCanCloseResponse;
import com.stockops.entity.Warehouse;
import com.stockops.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_READ')")
    public List<Warehouse> getAllWarehouses() {
        return warehouseService.findAll();
    }

    @GetMapping("/center/{centerId}")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_READ')")
    public List<Warehouse> getWarehousesByCenter(@PathVariable Long centerId) {
        return warehouseService.findByCenterId(centerId);
    }

    @GetMapping("/center/{centerId}/active")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_READ')")
    public List<Warehouse> getActiveWarehousesByCenter(@PathVariable Long centerId) {
        return warehouseService.findActiveByCenterId(centerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_READ')")
    public Warehouse getWarehouseById(@PathVariable Long id) {
        return warehouseService.findById(id);
    }

    @PostMapping("/center/{centerId}")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_CREATE')")
    public Warehouse createWarehouse(@PathVariable Long centerId, @RequestBody Warehouse warehouse) {
        return warehouseService.create(centerId, warehouse);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_UPDATE')")
    public Warehouse updateWarehouse(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        return warehouseService.update(id, warehouse);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_DELETE')")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/can-close")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_READ')")
    public WarehouseCanCloseResponse canCloseWarehouse(@PathVariable Long id) {
        return warehouseService.getCanCloseResponse(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@permissionChecker.hasPermission('WAREHOUSE_UPDATE')")
    public Warehouse closeWarehouse(@PathVariable Long id, @RequestBody CloseWarehouseRequest request) {
        return warehouseService.close(id, request.reason());
    }
}
