package com.stockops.repository;

import com.stockops.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for cycle count headers.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface CycleCountRepository extends JpaRepository<CycleCount, Long> {
}
