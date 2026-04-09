package com.stockops.repository;

import com.stockops.entity.ExpiryAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for expiry alert snapshots.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Repository
public interface ExpiryAlertRepository extends JpaRepository<ExpiryAlert, Long> {

    /**
     * Returns all active alerts that have not been acknowledged.
     *
     * @return unacknowledged alerts
     */
    List<ExpiryAlert> findByAcknowledgedFalse();

    /**
     * Returns all unacknowledged alerts for the supplied alert level.
     *
     * @param alertLevel alert severity level
     * @return matching unacknowledged alerts
     */
    List<ExpiryAlert> findByAlertLevelAndAcknowledgedFalse(String alertLevel);

    /**
     * Returns the number of unacknowledged alerts for the supplied alert level.
     *
     * @param alertLevel alert severity level
     * @return matching unacknowledged alert count
     */
    long countByAlertLevelAndAcknowledgedFalse(String alertLevel);

    /**
     * Returns alert history for a product.
     *
     * @param productId product id
     * @return alerts tied to the product
     */
    List<ExpiryAlert> findByProductId(Long productId);
}
