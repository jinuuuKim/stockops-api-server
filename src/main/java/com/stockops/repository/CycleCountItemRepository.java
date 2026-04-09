package com.stockops.repository;

import com.stockops.entity.CycleCountItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for cycle count detail rows.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface CycleCountItemRepository extends JpaRepository<CycleCountItem, Long> {

    /**
     * Loads count items for a cycle count in stable identifier order.
     *
     * @param cycleCountId cycle count identifier
     * @return matching count items
     */
    List<CycleCountItem> findByCycleCountIdOrderByIdAsc(Long cycleCountId);
}
