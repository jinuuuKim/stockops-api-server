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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void findAllFlatReturnsActiveCategories() {
        final Category cat = new Category();
        cat.setId(1L);
        cat.setCode("CAT001");
        cat.setName("Test Category");
        cat.setActive(true);

        when(categoryRepository.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(cat));

        final List<CategoryDTO> result = categoryService.findAllFlat();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("CAT001");
    }

    @Test
    void createCategoryThrowsWhenCodeExists() {
        when(categoryRepository.existsByCode("CAT001")).thenReturn(true);

        final CategoryRequestDTO request = new CategoryRequestDTO();
        request.setCode("CAT001");
        request.setName("Test");

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateCategoryThrowsWhenNotFound() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(999L, new CategoryRequestDTO()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCategoryThrowsWhenHasChildren() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category()));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteCategoryThrowsWhenHasProducts() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category()));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(productRepository.countByCategoryIdAndDeletedFalse(1L)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(ConflictException.class);
    }
}
