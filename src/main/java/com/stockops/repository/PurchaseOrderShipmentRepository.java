package com.stockops.repository;

import com.stockops.entity.PurchaseOrderShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderShipmentRepository extends JpaRepository<PurchaseOrderShipment, Long> {
    List<PurchaseOrderShipment> findByPurchaseOrderId(Long purchaseOrderId);
}
