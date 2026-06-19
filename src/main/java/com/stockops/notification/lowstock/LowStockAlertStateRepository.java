package com.stockops.notification.lowstock;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for low-stock webhook throttle state.
 *
 * @author StockOps Team
 * @since 2.5
 */
@Repository
public interface LowStockAlertStateRepository extends JpaRepository<LowStockAlertState, Long> {

    /**
     * Finds the throttle state for a scope.
     *
     * @param scopeKey scope key ("WAREHOUSE:&lt;id&gt;" or "GLOBAL")
     * @return state when present
     */
    Optional<LowStockAlertState> findByScopeKey(String scopeKey);
}
