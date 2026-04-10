package com.stockops.repository;

import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
    
    List<PurchaseOrder> findByRequestingCenterId(Long centerId);
    
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);
    
    List<PurchaseOrder> findByRequestingCenterIdAndStatus(Long centerId, PurchaseOrderStatus status);
    
    long countByPoNumberStartingWith(String prefix);
}
