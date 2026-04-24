package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.stockops.dto.ExcelEntityType;
import com.stockops.dto.ExcelImportResult;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.exception.ForbiddenException;
import com.stockops.repository.CenterRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.PurchaseOrderItemRepository;
import com.stockops.repository.PurchaseOrderRepository;
import com.stockops.repository.WarehouseRepository;
import com.stockops.security.CurrentUserProvider;
import com.stockops.security.ScopeGuard;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExcelImportServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private InboundService inboundService;

    @Mock
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private CenterRepository centerRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ScopeGuard scopeGuard;

    @Mock
    private Validator validator;

    @InjectMocks
    private ExcelImportService excelImportService;

    @Test
    void importWorkbookRecordsScopeErrorsForInboundRows() throws IOException {
        final Product product = new Product();
        product.setBarcode("P-101");

        final Location location = new Location();
        location.setCode("LOC-11");

        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(productRepository.findByBarcodeAndDeletedFalse("P-101")).thenReturn(Optional.of(product));
        when(locationRepository.findByCode("LOC-11")).thenReturn(Optional.of(location));
        when(validator.validate(any())).thenReturn(Collections.emptySet());
        doThrow(new ForbiddenException("Access denied for location: 11"))
                .when(scopeGuard).assertLocationAccess(11L);

        final ExcelImportResult result = excelImportService.importWorkbook(
                ExcelEntityType.INBOUNDS,
                new MockMultipartFile(
                        "file",
                        "inbounds.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        createInboundWorkbook()));

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.errors()).isNotEmpty();
    }

    private byte[] createInboundWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final var sheet = workbook.createSheet("inbounds");
            final var header = sheet.createRow(0);
            header.createCell(0).setCellValue("reference");
            header.createCell(1).setCellValue("inboundDate");
            header.createCell(2).setCellValue("supplier");
            header.createCell(3).setCellValue("productBarcode");
            header.createCell(4).setCellValue("lotNumber");
            header.createCell(5).setCellValue("expiryDate");
            header.createCell(6).setCellValue("quantity");
            header.createCell(7).setCellValue("locationCode");

            final var row = sheet.createRow(1);
            row.createCell(0).setCellValue("IN-1");
            row.createCell(1).setCellValue("2026-04-21");
            row.createCell(2).setCellValue("Scoped Supplier");
            row.createCell(3).setCellValue("P-101");
            row.createCell(4).setCellValue("LOT-101");
            row.createCell(5).setCellValue("2026-05-21");
            row.createCell(6).setCellValue(5);
            row.createCell(7).setCellValue("LOC-11");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
