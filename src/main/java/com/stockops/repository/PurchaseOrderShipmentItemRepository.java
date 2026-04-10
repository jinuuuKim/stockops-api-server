package com.stockops.repository;

import com.stockops.entity.PurchaseOrderShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderShipmentItemRepository extends JpaRepository<PurchaseOrderShipmentItem, Long> {
    List<PurchaseOrderShipmentItem> findByShipmentId(Long shipmentId);
}
