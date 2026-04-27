package com.stockops.controller;

import com.stockops.dto.PartialAcceptItem;
import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.PurchaseOrderShipment;
import com.stockops.entity.User;
import com.stockops.service.PurchaseOrderService;
import com.stockops.service.UserService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    private final UserService userService;

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_READ')")
    public List<PurchaseOrder> getAllPurchaseOrders() {
        return purchaseOrderService.findAll();
    }

    @GetMapping("/center/{centerId}")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_READ')")
    public List<PurchaseOrder> getPurchaseOrdersByCenter(@PathVariable Long centerId) {
        return purchaseOrderService.findByCenterId(centerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_READ')")
    public PurchaseOrder getPurchaseOrderById(@PathVariable Long id) {
        return purchaseOrderService.findById(id);
    }

    @GetMapping("/po-number/{poNumber}")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_READ')")
    public PurchaseOrder getPurchaseOrderByPoNumber(@PathVariable String poNumber) {
        return purchaseOrderService.findByPoNumber(poNumber);
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_CREATE')")
    public PurchaseOrder createPurchaseOrder(
            @RequestParam Long centerId,
            @RequestParam(required = false) Long warehouseId,
            Principal principal) {
        final User currentUser = userService.getUserByEmail(principal.getName());
        return purchaseOrderService.create(centerId, warehouseId, currentUser);
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_CREATE')")
    public PurchaseOrder addItem(
            @PathVariable Long id,
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        return purchaseOrderService.addItem(id, productId, quantity);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_CREATE')")
    public PurchaseOrder submitPurchaseOrder(@PathVariable Long id) {
        return purchaseOrderService.submit(id);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrder acceptPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String erpReference) {
        return purchaseOrderService.accept(id, erpReference);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrder rejectPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String reason) {
        return purchaseOrderService.reject(id, reason);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrder cancelPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String reason) {
        return purchaseOrderService.cancel(id, reason);
    }

    @PostMapping("/{id}/shipments")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrderShipment createShipment(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> shipmentData) {
        return purchaseOrderService.createShipment(
                id,
                shipmentData.get("shipmentNumber"),
                shipmentData.get("carrier"),
                shipmentData.get("trackingNumber")
        );
    }

    @PostMapping("/{id}/partial-accept")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrder partialAcceptPurchaseOrder(
            @PathVariable Long id,
            @RequestBody List<PartialAcceptItem> items) {
        return purchaseOrderService.partialAccept(id, items);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrder receiveShipment(
            @PathVariable Long id,
            @RequestParam Long shipmentId,
            Principal principal) {
        final User currentUser = userService.getUserByEmail(principal.getName());
        return purchaseOrderService.receiveShipment(id, shipmentId, currentUser.getId());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@permissionChecker.hasPermission('PURCHASE_ORDER_MANAGE')")
    public PurchaseOrder completePurchaseOrder(@PathVariable Long id, Principal principal) {
        final User currentUser = userService.getUserByEmail(principal.getName());
        return purchaseOrderService.complete(id, currentUser.getId());
    }
}
