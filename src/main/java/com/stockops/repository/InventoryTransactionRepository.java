package com.stockops.repository;

import com.stockops.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /**
     * Returns transaction history for a product.
     *
     * @param productId product id
     * @return transactions sorted by newest first
     */
    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);

    /**
     * Returns transaction history for a location.
     *
     * @param locationId location id
     * @return transactions sorted by newest first
     */
    List<InventoryTransaction> findByLocationIdOrderByCreatedAtDesc(Long locationId);

    /**
     * Returns transaction history for a lot.
     *
     * @param lotId lot id
     * @return transactions sorted by newest first
     */
    List<InventoryTransaction> findByLotIdOrderByCreatedAtDesc(Long lotId);

    /**
     * Returns transactions created within a date range.
     *
     * @param start inclusive range start
     * @param end inclusive range end
     * @return matching transactions sorted by newest first
     */
    @Query("SELECT t FROM InventoryTransaction t WHERE t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    List<InventoryTransaction> findByDateRange(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Counts transactions created within a date range.
     *
     * @param start inclusive range start
     * @param end inclusive range end
     * @return matching transaction count
     */
    long countByCreatedAtBetween(Instant start, Instant end);

    List<InventoryTransaction> findByTypeAndReferenceIdOrderByCreatedAtDesc(String type, Long referenceId);

    List<InventoryTransaction> findTop50ByOrderByCreatedAtDesc();

    /**
     * Returns transactions for a product within a date range.
     * Used by demand forecasting to analyze historical outbound patterns.
     *
     * @param productId product id
     * @param start range start
     * @param end range end
     * @return matching transactions sorted by newest first
     */
    @Query("SELECT t FROM InventoryTransaction t WHERE t.productId = :productId AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    List<InventoryTransaction> findByProductIdAndCreatedAtBetween(
            @Param("productId") Long productId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Returns outbound transactions for a set of locations within a date range.
     * Used by ABC/XYZ classification to compute product demand within a center's warehouses.
     *
     * @param locationIds location identifiers (warehouses in the center)
     * @param start inclusive range start
     * @param end inclusive range end
     * @return matching outbound transactions
     */
    @Query("SELECT t FROM InventoryTransaction t WHERE t.locationId IN :locationIds AND t.type = 'OUTBOUND' AND t.createdAt BETWEEN :start AND :end")
    List<InventoryTransaction> findOutboundByLocationIdsAndCreatedAtBetween(
            @Param("locationIds") List<Long> locationIds,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Sums outbound transaction quantities for a product within a date range.
     *
     * @param productId product id
     * @param type transaction type
     * @param start range start
     * @param end range end
     * @return total quantity for the given type
     */
    @Query("SELECT COALESCE(SUM(t.quantity), 0) FROM InventoryTransaction t WHERE t.productId = :productId AND t.type = :type AND t.createdAt BETWEEN :start AND :end")
    long sumQuantityByProductIdAndTypeAndCreatedAtBetween(
            @Param("productId") Long productId,
            @Param("type") String type,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Sums outbound transaction quantities grouped by product for locations in a date range.
     *
     * @param locationIds location ids
     * @param type transaction type
     * @param start range start
     * @param end range end
     * @return list of [productId, totalQuantity] pairs
     */
    @Query("SELECT t.productId, COALESCE(SUM(t.quantity), 0) FROM InventoryTransaction t WHERE t.locationId IN :locationIds AND t.type = :type AND t.createdAt BETWEEN :start AND :end GROUP BY t.productId")
    List<Object[]> sumQuantityByLocationIdsAndTypeAndCreatedAtBetween(
            @Param("locationIds") List<Long> locationIds,
            @Param("type") String type,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Sums transaction quantities of a type grouped by product across all locations within a date range.
     *
     * @param type transaction type
     * @param start range start
     * @param end range end
     * @return list of [productId, totalQuantity] pairs
     */
    @Query("SELECT t.productId, COALESCE(SUM(t.quantity), 0) FROM InventoryTransaction t WHERE t.type = :type AND t.createdAt BETWEEN :start AND :end GROUP BY t.productId")
    List<Object[]> sumQuantityByTypeAndCreatedAtBetween(
            @Param("type") String type,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
