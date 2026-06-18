package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.config.MetricsConfig;
import com.stockops.entity.ExpiryAlert;
import com.stockops.entity.Lot;
import com.stockops.repository.ExpiryAlertRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LotRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryAlertServiceTest {

    @Mock
    private ExpiryAlertRepository expiryAlertRepository;

    @Mock
    private LotRepository lotRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private MetricsConfig metricsConfig;

    @InjectMocks
    private ExpiryAlertService service;

    @Test
    void batchesStockLookupAndCreatesAlertsForInWindowLots() {
        final LocalDate today = LocalDate.now();
        final Lot near = lot(1L, "LOT-1", 100L, today.plusDays(5));
        final Lot far = lot(2L, "LOT-2", 200L, today.plusDays(100));

        when(expiryAlertRepository.findByAcknowledgedFalse()).thenReturn(List.of());
        when(lotRepository.findActiveLotsWithExpiry()).thenReturn(List.of(near, far));
        // Only the in-window lot (id 1) is queried; the far lot is filtered out before the lookup.
        when(inventoryRepository.sumPositiveQuantityByLotIdsIn(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 12L}));

        service.calculateExpiryAlerts();

        final ArgumentCaptor<ExpiryAlert> captor = ArgumentCaptor.forClass(ExpiryAlert.class);
        verify(expiryAlertRepository).save(captor.capture());
        final ExpiryAlert saved = captor.getValue();
        assertThat(saved.getLotId()).isEqualTo(1L);
        assertThat(saved.getQuantity()).isEqualTo(12);
        assertThat(saved.getDaysUntilExpiry()).isEqualTo(5);
        assertThat(saved.getAlertLevel()).isEqualTo("WARNING");

        // Stock is resolved by one grouped query, never per-lot.
        verify(inventoryRepository, never()).findByLotId(anyLong());
    }

    @Test
    void skipsLotsWithNoPositiveStock() {
        final LocalDate today = LocalDate.now();
        final Lot near = lot(1L, "LOT-1", 100L, today.plusDays(3));

        when(expiryAlertRepository.findByAcknowledgedFalse()).thenReturn(List.of());
        when(lotRepository.findActiveLotsWithExpiry()).thenReturn(List.of(near));
        // Lot absent from the grouped result => no positive stock => no alert.
        when(inventoryRepository.sumPositiveQuantityByLotIdsIn(List.of(1L))).thenReturn(List.of());

        service.calculateExpiryAlerts();

        verify(expiryAlertRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Lot lot(final Long id, final String number, final Long productId, final LocalDate expiry) {
        final Lot lot = new Lot();
        lot.setId(id);
        lot.setLotNumber(number);
        lot.setProductId(productId);
        lot.setExpiryDate(expiry);
        return lot;
    }
}
