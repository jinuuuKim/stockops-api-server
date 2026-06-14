package com.stockops.repository;

import com.stockops.entity.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for retail store master records.
 *
 * @author StockOps Team
 * @since 2.5
 */
public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByIdAndDeletedFalse(Long id);

    Optional<Store> findByCode(String code);

    boolean existsByCodeAndDeletedFalse(String code);
}
