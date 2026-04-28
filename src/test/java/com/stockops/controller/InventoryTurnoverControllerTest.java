package com.stockops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockops.dto.InventoryTurnoverDTO;
import com.stockops.report.InventoryTurnoverReportService;
import com.stockops.security.PermissionChecker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link com.stockops.report.InventoryTurnoverController} REST endpoint.
 * Covers the inventory turnover report endpoint with mocked service layer.
 *
 * @author StockOps Team
 * @since 2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class InventoryTurnoverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryTurnoverReportService reportService;

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

    private List<InventoryTurnoverDTO> sampleReport() {
        return List.of(new InventoryTurnoverDTO(
                1L,
                "Sample Product",
                "BAR-001",
                new BigDecimal("1000.00"),
                50,
                30,
                40,
                new BigDecimal("20000.00"),
                new BigDecimal("5.00"),
                30));
    }

    /**
     * Verifies that the inventory turnover report endpoint returns HTTP 200 with report data.
     */
    @Test
    void getInventoryTurnoverReturns200() throws Exception {
        stubPermissions();
        when(reportService.generateReport(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(sampleReport());

        mockMvc.perform(get("/api/v1/reports/inventory-turnover")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Sample Product"))
                .andExpect(jsonPath("$[0].turnoverRate").value(5.00));
    }

    /**
     * Verifies that the report endpoint with centerId filter returns HTTP 200.
     */
    @Test
    void getInventoryTurnoverWithCenterIdReturns200() throws Exception {
        stubPermissions();
        when(reportService.generateReport(any(LocalDate.class), any(LocalDate.class), anyLong()))
                .thenReturn(sampleReport());

        mockMvc.perform(get("/api/v1/reports/inventory-turnover")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .param("centerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Sample Product"));
    }

    /**
     * Verifies that the report endpoint returns HTTP 400 for invalid date format.
     */
    @Test
    void getInventoryTurnoverWithInvalidDateReturns400() throws Exception {
        stubPermissions();

        mockMvc.perform(get("/api/v1/reports/inventory-turnover")
                        .param("startDate", "invalid-date")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isBadRequest());
    }
}
