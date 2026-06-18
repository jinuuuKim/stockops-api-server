package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.entity.Inventory;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.exception.ForbiddenException;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransactionRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.LotRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.security.ScopeGuard;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LotRepository lotRepository;

    @Mock
    private ScopeGuard scopeGuard;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    @Test
    void getAllInventoryReturnsOnlyScopedRows() {
        final Inventory visibleInventory = inventory(1L, 100L, 1000L, 5);
        final Inventory hiddenInventory = inventory(2L, 200L, 2000L, 3);

        when(inventoryRepository.findAll()).thenReturn(List.of(visibleInventory, hiddenInventory));
        when(scopeGuard.filterByLocationScope(any(), any())).thenReturn(List.of(visibleInventory));
        when(productRepository.findAllById(any())).thenReturn(List.of(product(1000L, "P-1000", "Visible Product")));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location(100L, "LOC-100", "Scoped Location")));

        assertThat(inventoryQueryService.getAllInventory())
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(1L);
                    assertThat(dto.locationId()).isEqualTo(100L);
                    assertThat(dto.productName()).isEqualTo("Visible Product");
                });
    }

    @Test
    void getAllInventoryBatchesRelatedLookupsToAvoidNPlusOne() {
        final Inventory firstRow = inventory(1L, 100L, 1000L, 5);
        final Inventory secondRow = inventory(2L, 100L, 1000L, 3);

        when(inventoryRepository.findAll()).thenReturn(List.of(firstRow, secondRow));
        when(scopeGuard.filterByLocationScope(any(), any())).thenReturn(List.of(firstRow, secondRow));
        when(productRepository.findAllById(any())).thenReturn(List.of(product(1000L, "P-1000", "Visible Product")));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location(100L, "LOC-100", "Scoped Location")));

        assertThat(inventoryQueryService.getAllInventory()).hasSize(2);

        // Related entities are resolved with a single batched lookup per type, not once per row.
        verify(productRepository, times(1)).findAllById(any());
        verify(locationRepository, times(1)).findAllById(any());
        verify(productRepository, never()).findById(any());
        verify(locationRepository, never()).findById(any());
    }

    @Test
    void getInventoryByIdRejectsDirectAccessOutsideScope() {
        final Inventory inventory = inventory(9L, 900L, 9000L, 4);
        when(inventoryRepository.findById(9L)).thenReturn(Optional.of(inventory));
        doThrow(new ForbiddenException("Access denied for location: 900"))
                .when(scopeGuard).assertLocationAccess(900L);

        assertThatThrownBy(() -> inventoryQueryService.getInventoryById(9L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied for location: 900");
    }

    private Inventory inventory(final Long id, final Long locationId, final Long productId, final int quantity) {
        final Inventory inventory = new Inventory();
        inventory.setId(id);
        inventory.setLocationId(locationId);
        inventory.setProductId(productId);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(0);
        return inventory;
    }

    private Product product(final Long id, final String barcode, final String name) {
        final Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setBarcode(barcode);
        product.setName(name);
        return product;
    }

    private Location location(final Long id, final String code, final String name) {
        final Location location = new Location();
        location.setId(id);
        location.setCode(code);
        location.setName(name);
        return location;
    }
}
