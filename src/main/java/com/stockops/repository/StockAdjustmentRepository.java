package com.stockops.repository;

import com.stockops.entity.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for stock adjustment requests.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    /**
     * Finds adjustments by workflow status.
     *
     * @param status adjustment status
     * @return matching adjustments
     */
    List<StockAdjustment> findByStatus(String status);

    /**
     * Finds adjustment history for an inventory row.
     *
     * @param inventoryId inventory identifier
     * @return matching adjustments
     */
    List<StockAdjustment> findByInventoryId(Long inventoryId);

    /**
     * Counts adjustments by workflow status.
     *
     * @param status adjustment status
     * @return matching adjustment count
     */
    long countByStatus(String status);
}
