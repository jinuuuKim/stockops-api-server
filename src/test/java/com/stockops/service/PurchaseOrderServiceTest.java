package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.entity.Center;
import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.Warehouse;
import com.stockops.exception.ForbiddenException;
import com.stockops.repository.InboundItemRepository;
import com.stockops.repository.InboundRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.LotRepository;
import com.stockops.repository.PurchaseOrderItemRepository;
import com.stockops.repository.PurchaseOrderRepository;
import com.stockops.repository.PurchaseOrderShipmentItemRepository;
import com.stockops.repository.PurchaseOrderShipmentRepository;
import com.stockops.security.ScopeGuard;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Mock
    private PurchaseOrderShipmentRepository shipmentRepository;

    @Mock
    private PurchaseOrderShipmentItemRepository shipmentItemRepository;

    @Mock
    private CenterService centerService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ScopeGuard scopeGuard;

    @Mock
    private InboundRepository inboundRepository;

    @Mock
    private InboundItemRepository inboundItemRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LotRepository lotRepository;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    @Test
    void findByCenterIdReturnsEmptyListWhenCenterIsOutOfScope() {
        when(scopeGuard.filterCenterIds(List.of(2L))).thenReturn(List.of());

        assertThat(purchaseOrderService.findByCenterId(2L)).isEmpty();
        verify(purchaseOrderRepository, never()).findByRequestingCenterId(2L);
    }

    @Test
    void findByIdRejectsDirectAccessOutsideScope() {
        final Center center = new Center();
        center.setId(2L);

        final Warehouse warehouse = new Warehouse();
        warehouse.setId(20L);
        warehouse.setCenter(center);

        final PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(9L);
        purchaseOrder.setRequestingCenter(center);
        purchaseOrder.setTargetWarehouse(warehouse);

        when(purchaseOrderRepository.findById(9L)).thenReturn(Optional.of(purchaseOrder));
        org.mockito.Mockito.doThrow(new ForbiddenException("Access denied for warehouse: 20"))
                .when(scopeGuard).assertCenterWarehouseAccess(2L, 20L);

        assertThatThrownBy(() -> purchaseOrderService.findById(9L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied for warehouse: 20");
    }
}
