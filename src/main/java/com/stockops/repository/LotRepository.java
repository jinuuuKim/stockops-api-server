package com.stockops.repository;

import com.stockops.entity.Lot;
import com.stockops.entity.LotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for lot persistence and expiry-aware lookups.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByLotNumberAndProductId(String lotNumber, Long productId);

    /**
     * Finds active lots that are already expired as of the supplied business date.
     *
     * @param today business date used to evaluate expiry
     * @return active expired lots ordered by expiry date
     */
    @Query("""
            SELECT l
            FROM Lot l
            WHERE l.expiryDate < :today
              AND l.status = com.stockops.entity.LotStatus.ACTIVE
            ORDER BY l.expiryDate ASC,
                     l.id ASC
            """)
    List<Lot> findExpiredLots(@Param("today") LocalDate today);

    /**
     * Finds active lots with a populated expiry date.
     *
     * @return active lots that participate in expiry alert calculations
     */
    @Query("""
            SELECT l
            FROM Lot l
            WHERE l.status = com.stockops.entity.LotStatus.ACTIVE
              AND l.expiryDate IS NOT NULL
            ORDER BY l.expiryDate ASC,
                     l.id ASC
            """)
    List<Lot> findActiveLotsWithExpiry();

    /**
     * Finds active lots that can participate in FEFO allocation.
     * Only lots with an expiry date are returned so outbound confirmation consumes the earliest-expiring stock first.
     *
     * @param productId product id
     * @param status lot status
     * @return FEFO-ordered lots for the product
     */
    @Query("""
            SELECT l
            FROM Lot l
            WHERE l.productId = :productId
              AND l.status = :status
              AND l.expiryDate IS NOT NULL
            ORDER BY l.expiryDate ASC,
                     l.id ASC
            """)
    List<Lot> findActiveLotsByProductIdOrderByExpiryDateAsc(@Param("productId") Long productId,
                                                             @Param("status") LotStatus status);

    /**
     * Searches lots whose lot number contains the term (case-insensitive). Used by the assistant's
     * free-text inventory search so a user can look up stock by lot number; the caller normalizes
     * {@code LOT}/{@code LOT-} prefixes into multiple terms before querying.
     *
     * @param term     lot-number fragment to match
     * @param pageable result window (caps the number of matches)
     * @return matching lots
     */
    @Query("""
            SELECT l FROM Lot l
            WHERE LOWER(l.lotNumber) LIKE LOWER(CONCAT('%', :term, '%'))
            ORDER BY l.id ASC
            """)
    List<Lot> searchByLotNumber(@Param("term") String term, Pageable pageable);

}
