package com.stockops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockops.dto.AbcClassificationDTO;
import com.stockops.dto.AbcXyzMatrixDTO;
import com.stockops.dto.XyzClassificationDTO;
import com.stockops.security.PermissionChecker;
import com.stockops.service.AbcXyzReportService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link AbcXyzController} REST endpoints.
 * Covers ABC analysis, XYZ analysis, and ABC-XYZ matrix with mocked service layer.
 *
 * @author StockOps Team
 * @since 2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AbcXyzControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AbcXyzReportService abcXyzReportService;

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
     * Verifies that the ABC analysis endpoint returns HTTP 200 with classification data.
     */
    @Test
    void getAbcAnalysisReturns200() throws Exception {
        stubPermissions();
        when(abcXyzReportService.getAbcAnalysis(anyLong())).thenReturn(List.of(
                new AbcClassificationDTO(1L, "Product A", new BigDecimal("50000.00"), new BigDecimal("50.00"), "A"),
                new AbcClassificationDTO(2L, "Product B", new BigDecimal("30000.00"), new BigDecimal("80.00"), "B"),
                new AbcClassificationDTO(3L, "Product C", new BigDecimal("20000.00"), new BigDecimal("100.00"), "C")));

        mockMvc.perform(get("/api/v1/reports/abc-analysis")
                        .param("centerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].abcClass").value("A"))
                .andExpect(jsonPath("$[1].abcClass").value("B"))
                .andExpect(jsonPath("$[2].abcClass").value("C"));
    }

    /**
     * Verifies that the XYZ analysis endpoint returns HTTP 200 with classification data.
     */
    @Test
    void getXyzAnalysisReturns200() throws Exception {
        stubPermissions();
        when(abcXyzReportService.getXyzAnalysis(anyLong())).thenReturn(List.of(
                new XyzClassificationDTO(1L, "Stable Product", new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10.00"), "X"),
                new XyzClassificationDTO(2L, "Variable Product", new BigDecimal("80"), new BigDecimal("60"), new BigDecimal("75.00"), "Y"),
                new XyzClassificationDTO(3L, "Erratic Product", new BigDecimal("50"), new BigDecimal("80"), new BigDecimal("160.00"), "Z")));

        mockMvc.perform(get("/api/v1/reports/xyz-analysis")
                        .param("centerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].xyzClass").value("X"))
                .andExpect(jsonPath("$[1].xyzClass").value("Y"))
                .andExpect(jsonPath("$[2].xyzClass").value("Z"));
    }

    /**
     * Verifies that the ABC-XYZ matrix endpoint returns HTTP 200 with matrix structure.
     */
    @Test
    void getAbcXyzMatrixReturns200() throws Exception {
        stubPermissions();
        List<AbcXyzMatrixDTO.MatrixRow> rows = List.of(
                new AbcXyzMatrixDTO.MatrixRow("A", 1, 0, 0,
                        List.of(new AbcXyzMatrixDTO.MatrixCellProduct(1L, "Product A", new BigDecimal("50000.00"), new BigDecimal("10.00"))),
                        List.of(), List.of()),
                new AbcXyzMatrixDTO.MatrixRow("B", 0, 1, 0,
                        List.of(),
                        List.of(new AbcXyzMatrixDTO.MatrixCellProduct(2L, "Product B", new BigDecimal("30000.00"), new BigDecimal("75.00"))),
                        List.of()),
                new AbcXyzMatrixDTO.MatrixRow("C", 0, 0, 1,
                        List.of(), List.of(),
                        List.of(new AbcXyzMatrixDTO.MatrixCellProduct(3L, "Product C", new BigDecimal("20000.00"), new BigDecimal("160.00")))));
        when(abcXyzReportService.getAbcXyzMatrix(anyLong())).thenReturn(new AbcXyzMatrixDTO(rows, 3));

        mockMvc.perform(get("/api/v1/reports/abc-xyz-matrix")
                        .param("centerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProductCount").value(3))
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].abcClass").value("A"))
                .andExpect(jsonPath("$.rows[0].xCount").value(1))
                .andExpect(jsonPath("$.rows[1].abcClass").value("B"))
                .andExpect(jsonPath("$.rows[1].yCount").value(1))
                .andExpect(jsonPath("$.rows[2].abcClass").value("C"))
                .andExpect(jsonPath("$.rows[2].zCount").value(1));
    }

    /**
     * Verifies that the ABC analysis endpoint returns HTTP 400 when centerId is missing.
     */
    @Test
    void getAbcAnalysisWithoutCenterIdReturns400() throws Exception {
        stubPermissions();

        mockMvc.perform(get("/api/v1/reports/abc-analysis"))
                .andExpect(status().isBadRequest());
    }
}
