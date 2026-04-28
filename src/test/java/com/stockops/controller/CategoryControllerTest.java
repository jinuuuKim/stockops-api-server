package com.stockops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.dto.CategoryRequestDTO;
import com.stockops.entity.Category;
import com.stockops.repository.CategoryRepository;
import com.stockops.security.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link CategoryController} REST endpoints.
 * Covers CRUD operations with H2 in-memory database and mocked security.
 *
 * @author StockOps Team
 * @since 2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private PermissionChecker permissionChecker;

    private void stubPermissions() {
        when(permissionChecker.hasPermission(anyString())).thenReturn(true);
        when(permissionChecker.hasAnyPermission(any())).thenReturn(true);
        when(permissionChecker.hasCenterScope(anyLong())).thenReturn(true);
        when(permissionChecker.hasWarehouseScope(anyLong())).thenReturn(true);
        when(permissionChecker.hasPermissionForCenter(anyString(), anyLong())).thenReturn(true);
        when(permissionChecker.hasPermissionForWarehouse(anyString(), anyLong())).thenReturn(true);
    }

    /**
     * Verifies that listing categories returns HTTP 200 with a JSON array.
     */
    @Test
    void getCategoriesReturns200() throws Exception {
        stubPermissions();
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Verifies that retrieving a category by valid ID returns HTTP 200 with correct data.
     */
    @Test
    void getCategoryByIdReturns200() throws Exception {
        stubPermissions();
        Category category = categoryRepository.save(new Category("Test Category", "TEST-001", 1));

        mockMvc.perform(get("/api/v1/categories/{id}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Category"))
                .andExpect(jsonPath("$.code").value("TEST-001"))
                .andExpect(jsonPath("$.level").value(1));
    }

    /**
     * Verifies that retrieving a non-existent category returns HTTP 404.
     */
    @Test
    void getCategoryByIdReturns404() throws Exception {
        stubPermissions();
        mockMvc.perform(get("/api/v1/categories/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that creating a category returns HTTP 201 with location header and body.
     */
    @Test
    void createCategoryReturns201() throws Exception {
        stubPermissions();
        CategoryRequestDTO request = new CategoryRequestDTO("New Category", "NEW-001", null, 1, 0, true);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("New Category"))
                .andExpect(jsonPath("$.code").value("NEW-001"));
    }

    /**
     * Verifies that creating a category with duplicate code returns HTTP 400.
     */
    @Test
    void createCategoryWithDuplicateCodeReturns400() throws Exception {
        stubPermissions();
        categoryRepository.save(new Category("Existing", "DUP-001", 1));
        CategoryRequestDTO request = new CategoryRequestDTO("Duplicate", "DUP-001", null, 1, 0, true);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that updating a category returns HTTP 200 with updated data.
     */
    @Test
    void updateCategoryReturns200() throws Exception {
        stubPermissions();
        Category category = categoryRepository.save(new Category("Old Name", "UPD-001", 1));
        CategoryRequestDTO request = new CategoryRequestDTO("Updated Name", "UPD-001", null, 1, 0, true);

        mockMvc.perform(put("/api/v1/categories/{id}", category.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    /**
     * Verifies that deleting a category returns HTTP 204 and removes the category.
     */
    @Test
    void deleteCategoryReturns204() throws Exception {
        stubPermissions();
        Category category = categoryRepository.save(new Category("To Delete", "DEL-001", 1));
        Long id = category.getId();

        mockMvc.perform(delete("/api/v1/categories/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(id)).isEmpty();
    }
}
