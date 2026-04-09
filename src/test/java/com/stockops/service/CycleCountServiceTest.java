package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.dto.CycleCountItemDTO;
import com.stockops.entity.CycleCount;
import com.stockops.entity.CycleCountItem;
import com.stockops.entity.Inventory;
import com.stockops.entity.Lot;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.entity.User;
import com.stockops.exception.InvalidOperationException;
import com.stockops.repository.CycleCountItemRepository;
import com.stockops.repository.CycleCountRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LotRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CycleCountService}.
 *
 * @author StockOps Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class CycleCountServiceTest {

    @Mock
    private CycleCountRepository cycleCountRepository;

    @Mock
    private CycleCountItemRepository cycleCountItemRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LotRepository lotRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CycleCountService cycleCountService;

    /**
     * Verifies that recording a count updates actual quantity, variance, and count metadata.
     */
    @Test
    void recordCountUpdatesItemAndReturnsDto() {
        final CycleCountItem item = new CycleCountItem();
        item.setId(1L);
        item.setCycleCountId(10L);
        item.setInventoryId(100L);
        item.setExpectedQuantity(7);

        final Inventory inventory = new Inventory();
        inventory.setId(100L);
        inventory.setProductId(200L);
        inventory.setLocationId(300L);
        inventory.setLotId(400L);

        final Product product = new Product();
        product.setId(200L);
        product.setName("Milk");

        final Location location = new Location();
        location.setId(300L);
        location.setCode("A-01");

        final Lot lot = new Lot();
        lot.setId(400L);
        lot.setLotNumber("LOT-1");

        final User user = new User();
        user.setId(9L);
        user.setName("Staff User");

        when(cycleCountItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(cycleCountItemRepository.save(any(CycleCountItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findById(100L)).thenReturn(Optional.of(inventory));
        when(productRepository.findById(200L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(300L)).thenReturn(Optional.of(location));
        when(lotRepository.findById(400L)).thenReturn(Optional.of(lot));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        final CycleCountItemDTO dto = cycleCountService.recordCount(1L, 10, 9L, "checked twice");

        final ArgumentCaptor<CycleCountItem> captor = ArgumentCaptor.forClass(CycleCountItem.class);
        verify(cycleCountItemRepository).save(captor.capture());
        assertThat(captor.getValue().getActualQuantity()).isEqualTo(10);
        assertThat(captor.getValue().getVariance()).isEqualTo(3);
        assertThat(captor.getValue().getCountedBy()).isEqualTo(9L);
        assertThat(captor.getValue().getNotes()).isEqualTo("checked twice");
        assertThat(dto.productName()).isEqualTo("Milk");
        assertThat(dto.locationCode()).isEqualTo("A-01");
        assertThat(dto.lotNumber()).isEqualTo("LOT-1");
    }

    /**
     * Verifies that negative counted quantities are rejected.
     */
    @Test
    void recordCountRejectsNegativeQuantity() {
        assertThrows(InvalidOperationException.class,
                () -> cycleCountService.recordCount(1L, -1, 9L, null));
    }

    /**
     * Verifies that items are returned for a cycle count.
     */
    @Test
    void getItemsReturnsDtos() {
        final CycleCount cycleCount = new CycleCount();
        cycleCount.setId(10L);

        final CycleCountItem item = new CycleCountItem();
        item.setId(1L);
        item.setCycleCountId(10L);
        item.setInventoryId(100L);
        item.setExpectedQuantity(5);

        final Inventory inventory = new Inventory();
        inventory.setId(100L);
        inventory.setProductId(200L);
        inventory.setLocationId(300L);

        final Product product = new Product();
        product.setId(200L);
        product.setName("Water");

        final Location location = new Location();
        location.setId(300L);
        location.setCode("B-02");

        when(cycleCountRepository.findById(10L)).thenReturn(Optional.of(cycleCount));
        when(cycleCountItemRepository.findByCycleCountIdOrderByIdAsc(10L)).thenReturn(List.of(item));
        when(inventoryRepository.findById(100L)).thenReturn(Optional.of(inventory));
        when(productRepository.findById(200L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(300L)).thenReturn(Optional.of(location));

        final List<CycleCountItemDTO> items = cycleCountService.getItems(10L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).productName()).isEqualTo("Water");
        assertThat(items.get(0).locationCode()).isEqualTo("B-02");
    }
}
