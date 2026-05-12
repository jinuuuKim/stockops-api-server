package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stockops.dto.CategoryDTO;
import com.stockops.dto.CategoryRequestDTO;
import com.stockops.entity.Category;
import com.stockops.exception.ConflictException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.CategoryRepository;
import com.stockops.repository.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategorySucceeds() {
        final CategoryRequestDTO request = new CategoryRequestDTO("Test", "TEST", null, 1, 0, true);
        final Category saved = new Category("Test", "TEST", 1);
        ReflectionTestUtils.setField(saved, "id", 1L);
        saved.setActive(true);
        saved.setSortOrder(0);

        when(categoryRepository.existsByCode("TEST")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        final CategoryDTO result = categoryService.create(request);

        assertThat(result.name()).isEqualTo("Test");
        assertThat(result.code()).isEqualTo("TEST");
    }

    @Test
    void createCategoryThrowsWhenCodeExists() {
        final CategoryRequestDTO request = new CategoryRequestDTO("Test", "TEST", null, 1, 0, true);

        when(categoryRepository.existsByCode("TEST")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category code already exists");
    }

    @Test
    void deleteCategoryThrowsWhenProductsExist() {
        final Category category = new Category("Test", "TEST", 1);
        ReflectionTestUtils.setField(category, "id", 1L);
        category.setActive(true);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.countByCategoryIdAndDeletedFalse(1L)).thenReturn(5L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot delete category with linked products");
    }
}
