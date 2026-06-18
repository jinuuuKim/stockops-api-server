package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.dto.CategoryInventoryDTO;
import com.stockops.dto.SubcategoryInventoryDTO;
import com.stockops.entity.Category;
import com.stockops.entity.Inventory;
import com.stockops.entity.Product;
import com.stockops.repository.CategoryRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryInventoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private CategoryInventoryService service;

    @Test
    void aggregatesTreeWithBatchedInventoryAndCachedCategoryNames() {
        final Category root = category(1L, "Root", "C-ROOT", 1);
        final Category child = category(2L, "Child", "C-CHILD", 2);
        final Product product = product(10L, "P10", 2L, "100.00");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
        when(categoryRepository.findByParentIdAndActiveTrueOrderBySortOrderAsc(1L))
                .thenReturn(List.of(child));
        when(categoryRepository.findByParentIdAndActiveTrueOrderBySortOrderAsc(2L))
                .thenReturn(List.of());
        when(productRepository.findByCategoryIdInAndDeletedFalse(any()))
                .thenReturn(List.of(product));
        when(inventoryRepository.findByProductIdIn(any()))
                .thenReturn(List.of(inventory(10L, 5), inventory(10L, 3)));

        final CategoryInventoryDTO result = service.getCategoryInventorySummary(1L);

        assertThat(result.categoryName()).isEqualTo("Root");
        assertThat(result.totalProducts()).isEqualTo(1);
        assertThat(result.totalQuantity()).isEqualTo(8);
        assertThat(result.totalValue()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(result.subcategories()).singleElement().satisfies(sub -> {
            assertThat(sub.categoryId()).isEqualTo(2L);
            assertThat(sub.categoryName()).isEqualTo("Child");
            assertThat(sub.productCount()).isEqualTo(1);
            assertThat(sub.quantity()).isEqualTo(8);
        });

        // Inventory is fetched in one batched call (not per product), and the only category
        // findById is the root lookup — subcategory names come from the traversal cache.
        verify(inventoryRepository, never()).findByProductId(anyLong());
        verify(categoryRepository, times(1)).findById(anyLong());
    }

    private Category category(final Long id, final String name, final String code, final int level) {
        final Category category = new Category(name, code, level);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private Product product(final Long id, final String name, final Long categoryId, final String price) {
        final Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName(name);
        product.setBarcode("BAR-" + id);
        product.setCategoryId(categoryId);
        product.setDefaultPrice(new BigDecimal(price));
        return product;
    }

    private Inventory inventory(final Long productId, final int quantity) {
        final Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(0);
        return inventory;
    }
}
