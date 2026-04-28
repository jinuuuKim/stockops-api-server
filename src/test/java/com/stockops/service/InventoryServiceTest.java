package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stockops.entity.Inventory;
import com.stockops.entity.InventoryTransaction;
import com.stockops.entity.Lot;
import com.stockops.exception.InsufficientStockException;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransactionRepository;
import com.stockops.repository.LotRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private LotRepository lotRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void increaseStockThrowsWhenLotNotFound() {
        when(lotRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.increaseStock(1L, 1L, 1L, 10, "INBOUND", 1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void increaseStockThrowsWhenQuantityNotPositive() {
        final Lot lot = new Lot();
        lot.setId(1L);
        lot.setProductId(1L);

        when(lotRepository.findById(1L)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> inventoryService.increaseStock(1L, 1L, 1L, 0, "INBOUND", 1L, 1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void increaseStockCreatesNewInventoryWhenNoneExists() {
        final Lot lot = new Lot();
        lot.setId(1L);
        lot.setProductId(1L);

        when(lotRepository.findById(1L)).thenReturn(Optional.of(lot));
        when(inventoryRepository.findByProductIdAndLocationIdAndLotId(1L, 1L, 1L))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        final Inventory result = inventoryService.increaseStock(1L, 1L, 1L, 10, "INBOUND", 1L, 1L);

        assertThat(result.getQuantity()).isEqualTo(10);
    }

    @Test
    void decreaseStockThrowsWhenInsufficientStock() {
        final Lot lot = new Lot();
        lot.setId(1L);
        lot.setProductId(1L);

        final Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setQuantity(5);

        when(lotRepository.findById(1L)).thenReturn(Optional.of(lot));
        when(inventoryRepository.findByProductIdAndLocationIdAndLotId(1L, 1L, 1L))
                .thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.decreaseStock(1L, 1L, 1L, 10, "OUTBOUND", 1L, 1L))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void decreaseStockReducesQuantity() {
        final Lot lot = new Lot();
        lot.setId(1L);
        lot.setProductId(1L);

        final Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setQuantity(10);

        when(lotRepository.findById(1L)).thenReturn(Optional.of(lot));
        when(inventoryRepository.findByProductIdAndLocationIdAndLotId(1L, 1L, 1L))
                .thenReturn(Optional.of(inventory));
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        final Inventory result = inventoryService.decreaseStock(1L, 1L, 1L, 3, "OUTBOUND", 1L, 1L);

        assertThat(result.getQuantity()).isEqualTo(7);
    }
}
