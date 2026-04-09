package com.stockops.repository;

import com.stockops.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for inventory balance persistence and locked stock operations.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Loads a single inventory row with a pessimistic write lock.
     *
     * @param productId product id
     * @param locationId location id
     * @param lotId lot id
     * @return locked inventory row when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.productId = :productId
              AND i.locationId = :locationId
              AND ((:lotId IS NULL AND i.lotId IS NULL) OR i.lotId = :lotId)
            """)
    Optional<Inventory> findForUpdate(@Param("productId") Long productId,
                                      @Param("locationId") Long locationId,
                                      @Param("lotId") Long lotId);

    /**
     * Finds inventory without acquiring a lock.
     *
     * @param productId product id
     * @param locationId location id
     * @param lotId lot id
     * @return matching inventory row when present
     */
    Optional<Inventory> findByProductIdAndLocationIdAndLotId(Long productId, Long locationId, Long lotId);

    List<Inventory> findByProductId(Long productId);

    List<Inventory> findByLocationId(Long locationId);

    /**
     * Finds all inventory rows associated with a lot.
     *
     * @param lotId lot identifier
     * @return inventory rows tied to the lot
     */
    List<Inventory> findByLotId(Long lotId);

    /**
     * Finds inventory rows for a product and lot across all locations.
     *
     * @param productId product id
     * @param lotId lot id
     * @return matching inventory rows
     */
    List<Inventory> findByProductIdAndLotId(Long productId, Long lotId);

    /**
     * Sums total inventory quantity across all rows.
     *
     * @return total stored quantity
     */
    @Query("SELECT COALESCE(SUM(COALESCE(i.quantity, 0)), 0) FROM Inventory i")
    long sumInventoryQuantity();

    /**
     * Counts inventory rows whose available quantity is below the supplied threshold.
     *
     * @param threshold available quantity threshold
     * @return matching low-stock inventory row count
     */
    @Query("SELECT COUNT(i) FROM Inventory i WHERE (COALESCE(i.quantity, 0) - COALESCE(i.reservedQuantity, 0)) < :threshold")
    long countLowStockItems(@Param("threshold") int threshold);

    /**
     * Loads an inventory row by id with a pessimistic write lock.
     *
     * @param id inventory id
     * @return locked inventory row when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

}
