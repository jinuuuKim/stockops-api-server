package com.stockops.notification.lowstock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stockops.entity.Center;
import com.stockops.entity.Inventory;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.entity.Warehouse;
import com.stockops.notification.role.RoleWebhookConfig;
import com.stockops.notification.role.RoleWebhookConfigRepository;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfig.WebhookProviderType;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.WarehouseRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link LowStockWebhookService} detection, routing, and cooldown behaviour.
 *
 * @author StockOps Team
 * @since 2.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LowStockWebhookServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private WebhookEndpointConfigRepository webhookEndpointConfigRepository;
    @Mock private RoleWebhookConfigRepository roleWebhookConfigRepository;
    @Mock private WebhookService webhookService;
    @Mock private LowStockAlertStateRepository stateRepository;

    private LowStockProperties properties;
    private LowStockWebhookService service;

    @BeforeEach
    void setUp() {
        properties = new LowStockProperties();
        properties.setAdminLowRatio(0.5);
        service = new LowStockWebhookService(inventoryRepository, productRepository, locationRepository,
                warehouseRepository, webhookEndpointConfigRepository, roleWebhookConfigRepository,
                webhookService, stateRepository, properties);
        // No prior throttle state by default -> every scope is due.
        when(stateRepository.findByScopeKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void sendsWarehouseCardToWarehouseAndCenterEndpoints() {
        // Center 10, warehouse 100, one low SKU and one healthy SKU.
        final Center center = center(10L, "서울센터");
        final Warehouse warehouse = warehouse(100L, "강서창고", center);
        final Location location = location(1000L, warehouse);
        final Product low = product(1L, "냉동만두", 20);
        final Product healthy = product(2L, "감자", 5);

        when(inventoryRepository.findAll()).thenReturn(List.of(
                inventory(1L, low.getId(), location.getId(), 5, 0),     // available 5 <= 20 -> LOW
                inventory(2L, healthy.getId(), location.getId(), 50, 0) // available 50 > 5 -> ok
        ));
        when(productRepository.findAllById(any())).thenReturn(List.of(low, healthy));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location));
        when(warehouseRepository.findAllWithCenter()).thenReturn(List.of(warehouse));
        when(webhookEndpointConfigRepository.findByWarehouseIdAndEnabledTrue(100L))
                .thenReturn(List.of(endpoint("wh-url")));
        when(webhookEndpointConfigRepository.findByCenterIdAndWarehouseIdIsNullAndEnabledTrue(10L))
                .thenReturn(List.of(endpoint("center-url")));

        service.scanAndNotify();

        final ArgumentCaptor<WebhookPayload> payloadCaptor = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(webhookService).send(eq("TEAMS"), eq("wh-url"), payloadCaptor.capture());
        verify(webhookService).send(eq("TEAMS"), eq("center-url"), any(WebhookPayload.class));
        verify(webhookService, times(2)).send(any(), any(), any(WebhookPayload.class));

        final WebhookPayload payload = payloadCaptor.getValue();
        assertThat(payload.eventType()).isEqualTo("INVENTORY_LOW");
        assertThat(payload.alertType()).isEqualTo("LOW_STOCK");
        assertThat(payload.warehouseName()).isEqualTo("강서창고");
        assertThat(payload.eventTitle()).contains("강서창고").contains("1건");
        // Only the low SKU is listed, not the healthy one.
        assertThat(payload.details()).containsKey("냉동만두");
        assertThat(payload.details()).doesNotContainKey("감자");

        // Throttle state is recorded for the warehouse scope.
        verify(stateRepository).save(any(LowStockAlertState.class));
    }

    @Test
    void skipsWarehouseWithinCooldown() {
        final Warehouse warehouse = warehouse(100L, "강서창고", center(10L, "서울센터"));
        final Location location = location(1000L, warehouse);
        final Product low = product(1L, "냉동만두", 20);

        when(inventoryRepository.findAll()).thenReturn(List.of(
                inventory(1L, low.getId(), location.getId(), 5, 0)));
        when(productRepository.findAllById(any())).thenReturn(List.of(low));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location));
        when(warehouseRepository.findAllWithCenter()).thenReturn(List.of(warehouse));
        // Warehouse scope was notified 1 hour ago; cooldown is 6h -> not due.
        when(stateRepository.findByScopeKey("WAREHOUSE:100")).thenReturn(Optional.of(
                new LowStockAlertState("WAREHOUSE:100", Instant.now().minus(Duration.ofHours(1)), 1)));

        service.scanAndNotify();

        verify(webhookService, never()).send(any(), any(), any(WebhookPayload.class));
    }

    @Test
    void escalatesToAdminWhenLowShareReachesRatio() {
        // 2 of 3 tracked SKUs low (66% >= 50%) -> global escalation fires.
        final Warehouse warehouse = warehouse(100L, "강서창고", center(10L, "서울센터"));
        final Location location = location(1000L, warehouse);
        final Product low1 = product(1L, "냉동만두", 20);
        final Product low2 = product(2L, "아이스크림", 20);
        final Product healthy = product(3L, "감자", 5);

        when(inventoryRepository.findAll()).thenReturn(List.of(
                inventory(1L, low1.getId(), location.getId(), 1, 0),
                inventory(2L, low2.getId(), location.getId(), 0, 0),
                inventory(3L, healthy.getId(), location.getId(), 99, 0)));
        when(productRepository.findAllById(any())).thenReturn(List.of(low1, low2, healthy));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location));
        when(warehouseRepository.findAllWithCenter()).thenReturn(List.of(warehouse));
        when(webhookEndpointConfigRepository.findByWarehouseIdAndEnabledTrue(100L)).thenReturn(List.of());
        when(webhookEndpointConfigRepository.findByCenterIdAndWarehouseIdIsNullAndEnabledTrue(10L))
                .thenReturn(List.of());
        when(roleWebhookConfigRepository.findByRoleInAndEnabledTrue(List.of("ADMIN")))
                .thenReturn(List.of(roleConfig("ADMIN", "admin-url")));

        service.scanAndNotify();

        final ArgumentCaptor<WebhookPayload> payloadCaptor = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(webhookService).send(eq("TEAMS"), eq("admin-url"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().permissionLabel()).isEqualTo("최고관리자");
        assertThat(payloadCaptor.getValue().severity()).isEqualTo(WebhookPayload.Severity.CRITICAL);
    }

    @Test
    void doesNotEscalateToAdminBelowRatio() {
        // 1 of 3 tracked SKUs low (33% < 50%) -> no global escalation.
        final Warehouse warehouse = warehouse(100L, "강서창고", center(10L, "서울센터"));
        final Location location = location(1000L, warehouse);
        final Product low = product(1L, "냉동만두", 20);
        final Product ok1 = product(2L, "감자", 5);
        final Product ok2 = product(3L, "양파", 5);

        when(inventoryRepository.findAll()).thenReturn(List.of(
                inventory(1L, low.getId(), location.getId(), 1, 0),
                inventory(2L, ok1.getId(), location.getId(), 99, 0),
                inventory(3L, ok2.getId(), location.getId(), 99, 0)));
        when(productRepository.findAllById(any())).thenReturn(List.of(low, ok1, ok2));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location));
        when(warehouseRepository.findAllWithCenter()).thenReturn(List.of(warehouse));
        when(webhookEndpointConfigRepository.findByWarehouseIdAndEnabledTrue(100L)).thenReturn(List.of());
        when(webhookEndpointConfigRepository.findByCenterIdAndWarehouseIdIsNullAndEnabledTrue(10L))
                .thenReturn(List.of());

        service.scanAndNotify();

        verify(roleWebhookConfigRepository, never()).findByRoleInAndEnabledTrue(any());
        verify(webhookService, never()).send(any(), any(), any(WebhookPayload.class));
    }

    @Test
    void noopWhenDisabled() {
        properties.setEnabled(false);

        service.scanAndNotify();

        verifyNoInteractions(inventoryRepository);
        verifyNoInteractions(webhookService);
    }

    // --- fixtures ---

    private Product product(final Long id, final String name, final int safetyStock) {
        final Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName(name);
        product.setSafetyStockQuantity(safetyStock);
        return product;
    }

    private Inventory inventory(final Long id, final Long productId, final Long locationId,
                                final int quantity, final int reserved) {
        final Inventory inventory = new Inventory();
        inventory.setId(id);
        inventory.setProductId(productId);
        inventory.setLocationId(locationId);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(reserved);
        return inventory;
    }

    private Location location(final Long id, final Warehouse warehouse) {
        final Location location = new Location();
        location.setId(id);
        location.setWarehouse(warehouse);
        location.setCode("A-01");
        location.setName("A-01");
        location.setType("RACK");
        return location;
    }

    private Warehouse warehouse(final Long id, final String name, final Center center) {
        final Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setCode("WH-" + id);
        warehouse.setCenter(center);
        return warehouse;
    }

    private Center center(final Long id, final String name) {
        final Center center = new Center();
        center.setId(id);
        center.setName(name);
        center.setCode("C-" + id);
        return center;
    }

    private WebhookEndpointConfig endpoint(final String url) {
        final WebhookEndpointConfig endpoint = new WebhookEndpointConfig();
        ReflectionTestUtils.setField(endpoint, "id", Math.abs((long) url.hashCode()));
        endpoint.setProviderType(WebhookProviderType.TEAMS);
        endpoint.setWebhookUrl(url);
        endpoint.setEnabled(true);
        return endpoint;
    }

    private RoleWebhookConfig roleConfig(final String role, final String url) {
        final RoleWebhookConfig config = new RoleWebhookConfig();
        config.setRole(role);
        config.setWebhookUrl(url);
        config.setEnabled(true);
        return config;
    }
}
