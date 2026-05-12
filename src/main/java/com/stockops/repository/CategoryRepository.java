package com.stockops.repository;

import com.stockops.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Category entity.
 * Provides methods for hierarchical category queries.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Find all root categories (top-level 대분류).
     *
     * @return list of root categories
     */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true ORDER BY c.sortOrder")
    List<Category> findByParentIdIsNullAndActiveTrue();

    List<Category> findAllByActiveTrueOrderBySortOrderAsc();

    List<Category> findByParentIsNullAndActiveTrueOrderBySortOrderAsc();

    /**
     * Find children of a specific parent category.
     *
     * @param parentId parent category ID
     * @return list of child categories
     */
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.active = true ORDER BY c.sortOrder")
    List<Category> findByParentIdAndActiveTrue(@Param("parentId") Long parentId);

    List<Category> findByParentIdAndActiveTrueOrderBySortOrderAsc(Long parentId);

    /**
     * Find categories by level.
     *
     * @param level category level (1, 2, or 3)
     * @return list of categories at specified level
     */
    @Query("SELECT c FROM Category c WHERE c.level = :level AND c.active = true ORDER BY c.sortOrder")
    List<Category> findByLevelAndActiveTrue(@Param("level") Integer level);

    /**
     * Check if a category code already exists.
     *
     * @param code category code
     * @return true if code exists
     */
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.parent.id = :parentId")
    boolean existsByParentId(@Param("parentId") Long parentId);

    /**
     * Find category by code.
     *
     * @param code category code
     * @return category or null
     */
    Category findByCode(String code);
}
