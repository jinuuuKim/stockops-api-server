package com.stockops.repository;

import com.stockops.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

/**
 * Repository for Product entity persistence and queries.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Optional<Product> findByBarcodeAndDeletedFalse(String barcode);

    boolean existsByBarcodeAndDeletedFalse(String barcode);

    Page<Product> findAllByDeletedFalse(Pageable pageable);

    long countByCategoryIdAndDeletedFalse(Long categoryId);

    /**
     * Finds all active (non-deleted) products belonging to a specific category.
     *
     * @param categoryId category identifier
     * @return list of products in the category
     */
    List<Product> findByCategoryIdAndDeletedFalse(Long categoryId);

    /**
     * Finds all active products belonging to any of the given categories.
     *
     * @param categoryIds collection of category identifiers
     * @return list of products in the specified categories
     */
    List<Product> findByCategoryIdInAndDeletedFalse(List<Long> categoryIds);

    /**
     * Counts active products whose total available inventory (quantity - reservedQuantity)
     * falls below their safety stock threshold.
     *
     * <p>A product with no inventory rows at all counts as available=0, which is the most
     * critical case: zero stock vs. a non-zero safety target. The {@code @SQLRestriction}
     * on {@link Product} automatically excludes soft-deleted products.
     *
     * @return number of active products below safety stock
     */
    @Query("""
            SELECT COUNT(p.id)
            FROM Product p
            WHERE p.safetyStockQuantity > 0
              AND (
                SELECT COALESCE(SUM(COALESCE(i.quantity, 0) - COALESCE(i.reservedQuantity, 0)), 0)
                FROM Inventory i
                WHERE i.productId = p.id
              ) < p.safetyStockQuantity
            """)
    long countProductsBelowSafetyStock();

    /**
     * Searches active products whose name or barcode contains the query (case-insensitive).
     * Used by the assistant's free-text inventory search so users can look up stock by product
     * name or barcode instead of a numeric id.
     *
     * @param q        search keyword (matched against name and barcode)
     * @param pageable result window (caps the number of matches)
     * @return matching active products
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.deleted = false
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.id ASC
            """)
    List<Product> searchByNameOrBarcode(@Param("q") String q, Pageable pageable);
}
