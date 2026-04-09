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

    List<InventoryTransaction> findTop50ByOrderByCreatedAtDesc();
}
