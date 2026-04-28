package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.config.MetricsConfig;
import com.stockops.dto.AddInboundItemRequest;
import com.stockops.dto.CreateInboundRequest;
import com.stockops.entity.Inbound;
import com.stockops.entity.InboundItem;
import com.stockops.entity.Inventory;
import com.stockops.entity.Location;
import com.stockops.entity.Lot;
import com.stockops.entity.Product;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.inventory.WebSocketStockPublisher;
import com.stockops.repository.InboundItemRepository;
import com.stockops.repository.InboundRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.LotRepository;
import com.stockops.repository.ProductRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboundServiceTest {

    @Mock
    private InboundRepository inboundRepository;

    @Mock
    private InboundItemRepository inboundItemRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private LotRepository lotRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private WebSocketStockPublisher webSocketStockPublisher;

    @Mock
    private MetricsConfig metricsConfig;

    @InjectMocks
    private InboundService inboundService;

    @Test
    void createInboundReturnsDraftInbound() {
        final CreateInboundRequest request = new CreateInboundRequest();
        request.setWarehouseId(1L);
        request.setExpectedDate(LocalDate.of(2026, 5, 1));

        final Inbound saved = new Inbound();
        saved.setId(1L);
        saved.setStatus("DRAFT");
        saved.setWarehouseId(1L);

        when(inboundRepository.save(any(Inbound.class))).thenReturn(saved);

        final Inbound result = inboundService.createInbound(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void addItemToInboundThrowsWhenInboundNotFound() {
        when(inboundRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inboundService.addItem(999L, new AddInboundItemRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItemToInboundThrowsWhenInboundNotDraft() {
        final Inbound inbound = new Inbound();
        inbound.setId(1L);
        inbound.setStatus("CONFIRMED");

        when(inboundRepository.findById(1L)).thenReturn(Optional.of(inbound));

        assertThatThrownBy(() -> inboundService.addItem(1L, new AddInboundItemRequest()))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void confirmInboundThrowsWhenInboundNotFound() {
        when(inboundRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inboundService.confirmInbound(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
