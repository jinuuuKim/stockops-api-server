package com.stockops.controller;

import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.PurchaseOrderShipment;
import com.stockops.entity.User;
import com.stockops.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Purchase Order management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public List<PurchaseOrder> getAllPurchaseOrders() {
        return purchaseOrderService.findAll();
    }

    @GetMapping("/center/{centerId}")
    public List<PurchaseOrder> getPurchaseOrdersByCenter(@PathVariable Long centerId) {
        return purchaseOrderService.findByCenterId(centerId);
    }

    @GetMapping("/{id}")
    public PurchaseOrder getPurchaseOrderById(@PathVariable Long id) {
        return purchaseOrderService.findById(id);
    }

    @GetMapping("/po-number/{poNumber}")
    public PurchaseOrder getPurchaseOrderByPoNumber(@PathVariable String poNumber) {
        return purchaseOrderService.findByPoNumber(poNumber);
    }

    @PostMapping
    public PurchaseOrder createPurchaseOrder(
            @RequestParam Long centerId,
            @RequestParam(required = false) Long warehouseId,
            @AuthenticationPrincipal User currentUser) {
        return purchaseOrderService.create(centerId, warehouseId, currentUser);
    }

    @PostMapping("/{id}/items")
    public PurchaseOrder addItem(
            @PathVariable Long id,
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        return purchaseOrderService.addItem(id, productId, quantity);
    }

    @PostMapping("/{id}/submit")
    public PurchaseOrder submitPurchaseOrder(@PathVariable Long id) {
        return purchaseOrderService.submit(id);
    }

    @PostMapping("/{id}/accept")
    public PurchaseOrder acceptPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String erpReference) {
        return purchaseOrderService.accept(id, erpReference);
    }

    @PostMapping("/{id}/reject")
    public PurchaseOrder rejectPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String reason) {
        return purchaseOrderService.reject(id, reason);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrder cancelPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String reason) {
        return purchaseOrderService.cancel(id, reason);
    }

    @PostMapping("/{id}/shipments")
    public PurchaseOrderShipment createShipment(
            @PathVariable Long id,
            @RequestBody Map<String, String> shipmentData) {
        return purchaseOrderService.createShipment(
                id,
                shipmentData.get("shipmentNumber"),
                shipmentData.get("carrier"),
                shipmentData.get("trackingNumber")
        );
    }

    @PostMapping("/{id}/complete")
    public PurchaseOrder completePurchaseOrder(@PathVariable Long id) {
        return purchaseOrderService.complete(id);
    }
}
