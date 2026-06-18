package com.stockops.service;

import com.stockops.dto.CategoryInventoryDTO;
import com.stockops.dto.SubcategoryInventoryDTO;
import com.stockops.entity.Category;
import com.stockops.entity.Inventory;
import com.stockops.entity.Product;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.CategoryRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for category-based inventory aggregation.
 * Collects all category IDs in the subtree rooted at a given category,
 * then aggregates inventory across all products in those categories.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@Transactional(readOnly = true)
public class CategoryInventoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public CategoryInventoryService(final CategoryRepository categoryRepository,
                                    final ProductRepository productRepository,
                                    final InventoryRepository inventoryRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Gets aggregated inventory summary for a category and its entire subtree.
     * Recursively collects all descendant category IDs, queries products and inventory,
     * and returns aggregated totals plus per-subcategory breakdowns.
     *
     * @param categoryId root category identifier
     * @return aggregated inventory summary
     */
    public CategoryInventoryDTO getCategoryInventorySummary(final Long categoryId) {
        final Category root = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        final Set<Long> allCategoryIds = new HashSet<>();
        // Capture category names while traversing so per-subcategory name lookups don't re-query.
        final Map<Long, String> categoryNames = new HashMap<>();
        categoryNames.put(categoryId, root.getName());
        collectDescendantCategoryIds(categoryId, allCategoryIds, categoryNames);
        allCategoryIds.add(categoryId);

        final List<Product> allProducts = productRepository.findByCategoryIdInAndDeletedFalse(
                new ArrayList<>(allCategoryIds));

        final Map<Long, List<Product>> productsByCategory = allProducts.stream()
                .collect(Collectors.groupingBy(p -> p.getCategoryId() != null ? p.getCategoryId() : -1L));

        // Fetch inventory for all products in a single query, then group in memory by product,
        // instead of one findByProductId call per product.
        final List<Long> productIds = allProducts.stream().map(Product::getId).distinct().toList();
        final Map<Long, List<Inventory>> inventoryByProduct = productIds.isEmpty()
                ? Map.of()
                : inventoryRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(Inventory::getProductId));

        final List<SubcategoryInventoryDTO> subcategories = allCategoryIds.stream()
                .filter(id -> !id.equals(categoryId))
                .map(id -> buildSubcategoryDTO(id, categoryNames, productsByCategory, inventoryByProduct))
                .toList();

        long totalProducts = allProducts.size();
        int totalQuantity = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Product product : allProducts) {
            final List<Inventory> inventories = inventoryByProduct.getOrDefault(product.getId(), List.of());
            int productQty = inventories.stream()
                    .mapToInt(Inventory::getQuantity)
                    .sum();
            totalQuantity += productQty;
            totalValue = totalValue.add(product.getDefaultPrice().multiply(BigDecimal.valueOf(productQty)));
        }

        return new CategoryInventoryDTO(
                categoryId,
                root.getName(),
                totalProducts,
                totalQuantity,
                totalValue,
                subcategories
        );
    }

    private void collectDescendantCategoryIds(final Long parentId, final Set<Long> collected,
                                              final Map<Long, String> categoryNames) {
        final List<Category> children = categoryRepository.findByParentIdAndActiveTrueOrderBySortOrderAsc(parentId);
        for (Category child : children) {
            categoryNames.put(child.getId(), child.getName());
            if (collected.add(child.getId())) {
                collectDescendantCategoryIds(child.getId(), collected, categoryNames);
            }
        }
    }

    private SubcategoryInventoryDTO buildSubcategoryDTO(
            final Long catId,
            final Map<Long, String> categoryNames,
            final Map<Long, List<Product>> productsByCategory,
            final Map<Long, List<Inventory>> inventoryByProduct) {

        final String catName = categoryNames.getOrDefault(catId, "");

        final List<Product> products = productsByCategory.getOrDefault(catId, List.of());
        long productCount = products.size();
        int quantity = 0;
        BigDecimal value = BigDecimal.ZERO;

        for (Product product : products) {
            final List<Inventory> inventories = inventoryByProduct.getOrDefault(product.getId(), List.of());
            int productQty = inventories.stream()
                    .mapToInt(Inventory::getQuantity)
                    .sum();
            quantity += productQty;
            value = value.add(product.getDefaultPrice().multiply(BigDecimal.valueOf(productQty)));
        }

        return new SubcategoryInventoryDTO(catId, catName, productCount, quantity, value);
    }
}