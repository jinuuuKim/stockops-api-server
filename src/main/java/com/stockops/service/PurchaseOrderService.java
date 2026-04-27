package com.stockops.service;

import com.stockops.dto.PartialAcceptItem;
import com.stockops.entity.*;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.*;
import com.stockops.security.ScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for Purchase Order management.
 *
 * @author StockOps Team
 * @since 2.0
 * @see PurchaseOrderRepository
 * @see InboundService
 * @see InventoryService
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderService {

    private static final String REFERENCE_TYPE_PO = "PURCHASE_ORDER";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final PurchaseOrderShipmentItemRepository shipmentItemRepository;
    private final ProductRepository productRepository;
    private final CenterService centerService;
    private final WarehouseService warehouseService;
    private final NotificationService notificationService;
    private final ScopeGuard scopeGuard;
    private final InboundRepository inboundRepository;
    private final InboundItemRepository inboundItemRepository;
    private final InventoryService inventoryService;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;

    @Transactional(readOnly = true)
    public List<PurchaseOrder> findAll() {
        return filterScopedPurchaseOrders(purchaseOrderRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> findByCenterId(Long centerId) {
        if (!scopeGuard.filterCenterIds(List.of(centerId)).contains(centerId)) {
            return List.of();
        }
        return filterScopedPurchaseOrders(purchaseOrderRepository.findByRequestingCenterId(centerId));
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findById(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + id));
        assertPurchaseOrderAccess(purchaseOrder);
        return purchaseOrder;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findByPoNumber(String poNumber) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + poNumber));
        assertPurchaseOrderAccess(purchaseOrder);
        return purchaseOrder;
    }

    public PurchaseOrder create(Long centerId, Long warehouseId, User currentUser) {
        scopeGuard.assertCenterWarehouseAccess(centerId, warehouseId);
        Center center = centerService.findById(centerId);
        Warehouse warehouse = null;
        if (warehouseId != null) {
            warehouse = warehouseService.findById(warehouseId);
            if (warehouse.getCenter() == null || !centerId.equals(warehouse.getCenter().getId())) {
                throw new InvalidOperationException("Target warehouse must belong to the requesting center");
            }
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(generatePoNumber());
        po.setRequestingCenter(center);
        po.setTargetWarehouse(warehouse);
        po.setRequestedBy(currentUser);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        
        return purchaseOrderRepository.save(po);
    }

    public PurchaseOrder addItem(Long poId, Long productId, Integer quantity) {
        PurchaseOrder po = findById(poId);
        
        Product product = productRepository.getReferenceById(productId);
        
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(po);
        item.setProduct(product);
        item.setRequestedQuantity(quantity);
        
        purchaseOrderItemRepository.save(item);
        po.getItems().add(item);
        
        return purchaseOrderRepository.save(po);
    }

    public PurchaseOrder submit(Long poId) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new InvalidOperationException("Only DRAFT orders can be submitted");
        }
        
        if (po.getItems().isEmpty()) {
            throw new InvalidOperationException("Cannot submit an order without items");
        }
        
        po.setStatus(PurchaseOrderStatus.REQUESTED);
        po.setRequestedAt(LocalDateTime.now());

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    public PurchaseOrder accept(Long poId, String erpReference) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.REQUESTED) {
            throw new InvalidOperationException("Only REQUESTED orders can be accepted");
        }
        
        po.setStatus(PurchaseOrderStatus.ACCEPTED);
        po.setErpReference(erpReference);
        po.setErpRespondedAt(LocalDateTime.now());

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    public PurchaseOrder reject(Long poId, String reason) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.REQUESTED) {
            throw new InvalidOperationException("Only REQUESTED orders can be rejected");
        }
        
        po.setStatus(PurchaseOrderStatus.REJECTED);
        po.setCancelReason(reason);
        po.setErpRespondedAt(LocalDateTime.now());

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    public PurchaseOrder cancel(Long poId, String reason) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() == PurchaseOrderStatus.COMPLETED) {
            throw new InvalidOperationException("Cannot cancel a COMPLETED order");
        }
        
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setCancelReason(reason);

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    public PurchaseOrderShipment createShipment(Long poId, String shipmentNumber, String carrier, String trackingNumber) {
        PurchaseOrder po = findById(poId);

        if (po.getStatus() != PurchaseOrderStatus.ACCEPTED &&
            po.getStatus() != PurchaseOrderStatus.PARTIALLY_ACCEPTED) {
            throw new InvalidOperationException("Only ACCEPTED orders can have shipments created");
        }

        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setPurchaseOrder(po);
        shipment.setShipmentNumber(shipmentNumber);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.CREATED);

        po.setStatus(PurchaseOrderStatus.SHIPMENT_CREATED);

        shipmentRepository.save(shipment);
        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder.getShipments().get(savedPurchaseOrder.getShipments().size() - 1);
    }

    /**
     * Partially accepts a purchase order with per-item accepted quantities.
     * Typically called by ERP when it cannot fulfill the full requested quantity.
     *
     * @param poId purchase order identifier
     * @param items list of partial acceptance items with accepted quantities
     * @return updated purchase order with PARTIALLY_ACCEPTED status
     * @throws ResourceNotFoundException when the purchase order or an item is not found
     * @throws InvalidOperationException when the order cannot be partially accepted or quantities are invalid
     */
    public PurchaseOrder partialAccept(Long poId, List<PartialAcceptItem> items) {
        PurchaseOrder po = findById(poId);

        if (po.getStatus() != PurchaseOrderStatus.REQUESTED && po.getStatus() != PurchaseOrderStatus.ACCEPTED) {
            throw new InvalidOperationException("Only REQUESTED or ACCEPTED orders can be partially accepted");
        }

        if (items == null || items.isEmpty()) {
            throw new InvalidOperationException("Partial accept requires at least one item");
        }

        for (PartialAcceptItem partialItem : items) {
            PurchaseOrderItem poItem = po.getItems().stream()
                    .filter(i -> i.getId().equals(partialItem.poItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("PO item not found: " + partialItem.poItemId()));

            if (partialItem.acceptedQuantity() == null || partialItem.acceptedQuantity() < 0
                    || partialItem.acceptedQuantity() > poItem.getRequestedQuantity()) {
                throw new InvalidOperationException("Accepted quantity must be between 0 and requested quantity for item: " + partialItem.poItemId());
            }

            poItem.setAcceptedQuantity(partialItem.acceptedQuantity());
            purchaseOrderItemRepository.save(poItem);
        }

        po.setStatus(PurchaseOrderStatus.PARTIALLY_ACCEPTED);

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    /**
     * Receives a shipment and creates inbound records with inventory updates.
     * Transitions the purchase order to INBOUND_PENDING or COMPLETED depending on
     * whether all shipments have been delivered.
     *
     * @param poId purchase order identifier
     * @param shipmentId shipment identifier to receive
     * @param userId identifier of the user performing the receipt
     * @return updated purchase order
     * @throws ResourceNotFoundException when the purchase order or shipment is not found
     * @throws InvalidOperationException when the order or shipment cannot be received
     */
    public PurchaseOrder receiveShipment(Long poId, Long shipmentId, Long userId) {
        PurchaseOrder po = findById(poId);

        if (po.getStatus() != PurchaseOrderStatus.SHIPMENT_CREATED
                && po.getStatus() != PurchaseOrderStatus.INBOUND_PENDING) {
            throw new InvalidOperationException("Only orders with shipments can be received");
        }

        PurchaseOrderShipment shipment = po.getShipments().stream()
                .filter(s -> s.getId().equals(shipmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new InvalidOperationException("Shipment already delivered: " + shipmentId);
        }

        Location targetLocation = resolveTargetLocation(po);
        List<PurchaseOrderShipmentItem> shipmentItems = shipmentItemRepository.findByShipmentId(shipmentId);

        Inbound inbound = new Inbound();
        inbound.setInboundDate(LocalDate.now());
        inbound.setSupplier(po.getSupplierName());
        inbound.setStatus("CONFIRMED");
        inbound.setTotalQuantity(0);
        inbound.setCreatedBy(userId);
        inbound = inboundRepository.save(inbound);

        int totalQuantity = 0;

        if (!shipmentItems.isEmpty()) {
            for (PurchaseOrderShipmentItem shipmentItem : shipmentItems) {
                PurchaseOrderItem poItem = shipmentItem.getPurchaseOrderItem();
                Product product = poItem.getProduct();
                int quantity = nullSafeQuantity(shipmentItem.getShippedQuantity());
                createInboundItemAndUpdateInventory(inbound, product, quantity, targetLocation, userId);
                totalQuantity += quantity;
            }
        } else {
            for (PurchaseOrderItem poItem : po.getItems()) {
                Product product = poItem.getProduct();
                int quantity = poItem.getAcceptedQuantity() != null && poItem.getAcceptedQuantity() > 0
                        ? poItem.getAcceptedQuantity()
                        : poItem.getRequestedQuantity();
                createInboundItemAndUpdateInventory(inbound, product, quantity, targetLocation, userId);
                totalQuantity += quantity;
            }
        }

        inbound.setTotalQuantity(totalQuantity);
        inboundRepository.save(inbound);

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());
        shipmentRepository.save(shipment);

        boolean allDelivered = po.getShipments().stream()
                .allMatch(s -> s.getStatus() == ShipmentStatus.DELIVERED);

        if (allDelivered) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
        } else {
            po.setStatus(PurchaseOrderStatus.INBOUND_PENDING);
        }

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    /**
     * Completes a purchase order by receiving all undelivered shipments,
     * creating inbound records, and updating inventory.
     * If the order is already INBOUND_PENDING, it simply finalizes to COMPLETED.
     *
     * @param poId purchase order identifier
     * @param userId identifier of the user completing the order
     * @return updated purchase order with COMPLETED status
     * @throws ResourceNotFoundException when the purchase order is not found
     * @throws InvalidOperationException when the order cannot be completed
     */
    public PurchaseOrder complete(Long poId, Long userId) {
        PurchaseOrder po = findById(poId);

        if (po.getStatus() == PurchaseOrderStatus.SHIPMENT_CREATED) {
            for (PurchaseOrderShipment shipment : po.getShipments()) {
                if (shipment.getStatus() != ShipmentStatus.DELIVERED) {
                    processShipmentReceipt(po, shipment, userId);
                }
            }
        } else if (po.getStatus() != PurchaseOrderStatus.INBOUND_PENDING) {
            throw new InvalidOperationException("Only SHIPMENT_CREATED or INBOUND_PENDING orders can be completed");
        }

        po.setStatus(PurchaseOrderStatus.COMPLETED);

        final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(po);
        notificationService.createPurchaseOrderStatusNotification(savedPurchaseOrder, savedPurchaseOrder.getStatus());
        return savedPurchaseOrder;
    }

    private List<PurchaseOrder> filterScopedPurchaseOrders(final List<PurchaseOrder> purchaseOrders) {
        return scopeGuard.filterByCenterWarehouseScope(
                purchaseOrders,
                purchaseOrder -> purchaseOrder.getRequestingCenter() == null ? null : purchaseOrder.getRequestingCenter().getId(),
                purchaseOrder -> purchaseOrder.getTargetWarehouse() == null ? null : purchaseOrder.getTargetWarehouse().getId());
    }

    private void assertPurchaseOrderAccess(final PurchaseOrder purchaseOrder) {
        scopeGuard.assertCenterWarehouseAccess(
                purchaseOrder.getRequestingCenter() == null ? null : purchaseOrder.getRequestingCenter().getId(),
                purchaseOrder.getTargetWarehouse() == null ? null : purchaseOrder.getTargetWarehouse().getId());
    }

    private String generatePoNumber() {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = purchaseOrderRepository.countByPoNumberStartingWith("PO-" + datePrefix) + 1;
        return String.format("PO-%s-%03d", datePrefix, count);
    }

    private Location resolveTargetLocation(final PurchaseOrder po) {
        if (po.getTargetWarehouse() != null) {
            List<Location> locations = locationRepository.findByWarehouseId(po.getTargetWarehouse().getId());
            if (!locations.isEmpty()) {
                return locations.get(0);
            }
        }

        List<Location> receivingLocations = locationRepository.findByType("RECEIVING");
        if (!receivingLocations.isEmpty()) {
            return receivingLocations.get(0);
        }

        throw new InvalidOperationException("No suitable location found for inbound");
    }

    private void createInboundItemAndUpdateInventory(final Inbound inbound,
                                                      final Product product,
                                                      final int quantity,
                                                      final Location location,
                                                      final Long userId) {
        String lotNumber = "AUTO-PO-" + inbound.getId() + "-P" + product.getId();

        Lot lot = lotRepository.findByLotNumberAndProductId(lotNumber, product.getId())
                .orElseGet(() -> {
                    Lot newLot = new Lot();
                    newLot.setLotNumber(lotNumber);
                    newLot.setProductId(product.getId());
                    newLot.setReceivedDate(LocalDate.now());
                    newLot.setQuantity(0);
                    newLot.setStatus(LotStatus.ACTIVE);
                    return lotRepository.save(newLot);
                });

        InboundItem inboundItem = new InboundItem();
        inboundItem.setInboundId(inbound.getId());
        inboundItem.setProductId(product.getId());
        inboundItem.setLotNumber(lotNumber);
        inboundItem.setQuantity(quantity);
        inboundItem.setLocationId(location.getId());
        inboundItemRepository.save(inboundItem);

        inventoryService.increaseStock(
                product.getId(),
                location.getId(),
                lot.getId(),
                quantity,
                REFERENCE_TYPE_PO,
                inbound.getId(),
                userId
        );

        lot.setQuantity(nullSafeQuantity(lot.getQuantity()) + quantity);
        lotRepository.save(lot);
    }

    private void processShipmentReceipt(final PurchaseOrder po,
                                         final PurchaseOrderShipment shipment,
                                         final Long userId) {
        Location targetLocation = resolveTargetLocation(po);
        List<PurchaseOrderShipmentItem> shipmentItems = shipmentItemRepository.findByShipmentId(shipment.getId());

        Inbound inbound = new Inbound();
        inbound.setInboundDate(LocalDate.now());
        inbound.setSupplier(po.getSupplierName());
        inbound.setStatus("CONFIRMED");
        inbound.setTotalQuantity(0);
        inbound.setCreatedBy(userId);
        inbound = inboundRepository.save(inbound);

        int totalQuantity = 0;

        if (!shipmentItems.isEmpty()) {
            for (PurchaseOrderShipmentItem shipmentItem : shipmentItems) {
                PurchaseOrderItem poItem = shipmentItem.getPurchaseOrderItem();
                Product product = poItem.getProduct();
                int quantity = nullSafeQuantity(shipmentItem.getShippedQuantity());
                createInboundItemAndUpdateInventory(inbound, product, quantity, targetLocation, userId);
                totalQuantity += quantity;
            }
        } else {
            for (PurchaseOrderItem poItem : po.getItems()) {
                Product product = poItem.getProduct();
                int quantity = poItem.getAcceptedQuantity() != null && poItem.getAcceptedQuantity() > 0
                        ? poItem.getAcceptedQuantity()
                        : poItem.getRequestedQuantity();
                createInboundItemAndUpdateInventory(inbound, product, quantity, targetLocation, userId);
                totalQuantity += quantity;
            }
        }

        inbound.setTotalQuantity(totalQuantity);
        inboundRepository.save(inbound);

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());
        shipmentRepository.save(shipment);
    }

    private int nullSafeQuantity(final Integer quantity) {
        return quantity == null ? 0 : quantity;
    }
}
