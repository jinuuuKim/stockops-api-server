package com.stockops.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.dto.InventoryTurnoverDTO;
import com.stockops.entity.Product;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransactionRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryTurnoverReportServiceTest {

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private InventoryTurnoverReportService service;

    @Test
    void generateReportBatchesAggregatesAndComputesTurnover() {
        // Product 1: ending=30, outbound=20 -> beginning=50, avg=40, cogs=20000, value=40000,
        // rawRate=0.5, annualized over 30 days (factor 365/30) -> 6.08.
        when(transactionRepository.sumQuantityByTypeAndCreatedAtBetween(
                anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 20L}));
        when(inventoryRepository.sumQuantityGroupedByProduct())
                .thenReturn(List.<Object[]>of(new Object[]{1L, 30L}));
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(product(1L, "BAR-1", "Product One", "1000.00")));

        final List<InventoryTurnoverDTO> report =
                service.generateReport(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30));

        assertThat(report).singleElement().satisfies(dto -> {
            assertThat(dto.productId()).isEqualTo(1L);
            assertThat(dto.productName()).isEqualTo("Product One");
            assertThat(dto.beginningInventoryQty()).isEqualTo(50);
            assertThat(dto.endingInventoryQty()).isEqualTo(30);
            assertThat(dto.turnoverRate()).isEqualByComparingTo(new BigDecimal("6.08"));
        });

        // Confirms the per-product N+1 aggregate calls are no longer used.
        verify(transactionRepository, never())
                .sumQuantityByProductIdAndTypeAndCreatedAtBetween(anyLong(), anyString(),
                        any(Instant.class), any(Instant.class));
        verify(inventoryRepository, never()).sumQuantityByProductId(anyLong());
    }

    @Test
    void generateReportSkipsProductsMissingFromCatalog() {
        // Product 2 has inventory rows but is absent from findAllById (e.g. soft-deleted) -> skipped.
        when(transactionRepository.sumQuantityByTypeAndCreatedAtBetween(
                anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(inventoryRepository.sumQuantityGroupedByProduct())
                .thenReturn(List.of(new Object[]{1L, 30L}, new Object[]{2L, 10L}));
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(product(1L, "BAR-1", "Product One", "1000.00")));

        final List<InventoryTurnoverDTO> report =
                service.generateReport(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30));

        assertThat(report).extracting(InventoryTurnoverDTO::productId).containsExactly(1L);
    }

    private Product product(final Long id, final String barcode, final String name, final String price) {
        final Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setBarcode(barcode);
        product.setName(name);
        product.setDefaultPrice(new BigDecimal(price));
        return product;
    }
}
