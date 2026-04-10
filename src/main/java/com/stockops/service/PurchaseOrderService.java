package com.stockops.service;

import com.stockops.entity.*;
import com.stockops.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Service for Purchase Order management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final CenterService centerService;
    private final WarehouseService warehouseService;

    public List<PurchaseOrder> findAll() {
        return purchaseOrderRepository.findAll();
    }

    public List<PurchaseOrder> findByCenterId(Long centerId) {
        return purchaseOrderRepository.findByRequestingCenterId(centerId);
    }

    public PurchaseOrder findById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found: " + id));
    }

    public PurchaseOrder findByPoNumber(String poNumber) {
        return purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found: " + poNumber));
    }

    public PurchaseOrder create(Long centerId, Long warehouseId, User currentUser) {
        Center center = centerService.findById(centerId);
        Warehouse warehouse = null;
        if (warehouseId != null) {
            warehouse = warehouseService.findById(warehouseId);
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
        
        Product product = new Product();
        product.setId(productId);
        
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
            throw new RuntimeException("Only DRAFT orders can be submitted");
        }
        
        if (po.getItems().isEmpty()) {
            throw new RuntimeException("Cannot submit an order without items");
        }
        
        po.setStatus(PurchaseOrderStatus.REQUESTED);
        po.setRequestedAt(LocalDateTime.now());
        
        return purchaseOrderRepository.save(po);
    }

    public PurchaseOrder accept(Long poId, String erpReference) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.REQUESTED) {
            throw new RuntimeException("Only REQUESTED orders can be accepted");
        }
        
        po.setStatus(PurchaseOrderStatus.ACCEPTED);
        po.setErpReference(erpReference);
        po.setErpRespondedAt(LocalDateTime.now());
        
        return purchaseOrderRepository.save(po);
    }

    public PurchaseOrder reject(Long poId, String reason) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.REQUESTED) {
            throw new RuntimeException("Only REQUESTED orders can be rejected");
        }
        
        po.setStatus(PurchaseOrderStatus.REJECTED);
        po.setCancelReason(reason);
        po.setErpRespondedAt(LocalDateTime.now());
        
        return purchaseOrderRepository.save(po);
    }

    public PurchaseOrder cancel(Long poId, String reason) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() == PurchaseOrderStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a COMPLETED order");
        }
        
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setCancelReason(reason);
        
        return purchaseOrderRepository.save(po);
    }

    public PurchaseOrderShipment createShipment(Long poId, String shipmentNumber, String carrier, String trackingNumber) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.ACCEPTED && 
            po.getStatus() != PurchaseOrderStatus.PARTIALLY_ACCEPTED) {
            throw new RuntimeException("Only ACCEPTED orders can have shipments created");
        }
        
        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setPurchaseOrder(po);
        shipment.setShipmentNumber(shipmentNumber);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.CREATED);
        
        po.setStatus(PurchaseOrderStatus.SHIPMENT_CREATED);
        
        shipmentRepository.save(shipment);
        return purchaseOrderRepository.save(po).getShipments().get(po.getShipments().size() - 1);
    }

    public PurchaseOrder complete(Long poId) {
        PurchaseOrder po = findById(poId);
        
        if (po.getStatus() != PurchaseOrderStatus.SHIPMENT_CREATED) {
            throw new RuntimeException("Only orders with shipments can be completed");
        }
        
        po.setStatus(PurchaseOrderStatus.COMPLETED);
        
        return purchaseOrderRepository.save(po);
    }

    private String generatePoNumber() {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = purchaseOrderRepository.countByPoNumberPrefix("PO-" + datePrefix) + 1;
        return String.format("PO-%s-%03d", datePrefix, count);
    }
}
