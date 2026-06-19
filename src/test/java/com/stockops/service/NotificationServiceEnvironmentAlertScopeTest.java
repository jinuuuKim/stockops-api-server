package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.stockops.config.MetricsConfig;
import com.stockops.entity.AlertSeverity;
import com.stockops.entity.Center;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.Notification;
import com.stockops.entity.NotificationType;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.User;
import com.stockops.entity.Warehouse;
import com.stockops.repository.EnvironmentAlertRepository;
import com.stockops.repository.ExpiryAlertRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.NotificationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.SensorDeviceRepository;
import com.stockops.repository.WarehouseRepository;
import com.stockops.security.ScopeAccessProfile;
import com.stockops.security.ScopeAccessService;
import com.stockops.security.ScopeAssignment;
import com.stockops.security.ScopeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Verifies that sensor (environment) alerts surface in the in-app notification center only for
 * users whose role scope grants visibility over the sensor's warehouse — the warehouse manager,
 * the parent center manager, or an administrator — and never for store-scoped users.
 *
 * <p>The alert under test belongs to sensor 5 → warehouse 10 → center 1.
 *
 * @author StockOps Team
 * @since 2.6
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceEnvironmentAlertScopeTest {

    private static final Long WAREHOUSE_ID = 10L;
    private static final Long CENTER_ID = 1L;

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserService userService;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ExpiryAlertRepository expiryAlertRepository;
    @Mock private EnvironmentAlertRepository environmentAlertRepository;
    @Mock private SensorDeviceRepository sensorDeviceRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ScopeAccessService scopeAccessService;
    @Mock private MetricsConfig metricsConfig;

    private NotificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userService, inventoryRepository,
                productRepository, locationRepository, expiryAlertRepository, environmentAlertRepository,
                sensorDeviceRepository, warehouseRepository, scopeAccessService, metricsConfig);

        user = new User();
        user.setId(7L);
        user.setEmail("u@example.com");
        when(userService.getUserByEmail("u@example.com")).thenReturn(user);

        // Low-stock and expiry sync are no-ops for this test.
        when(inventoryRepository.findAll()).thenReturn(List.of());
        when(expiryAlertRepository.findByAcknowledgedFalse()).thenReturn(List.of());
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(locationRepository.findAllById(any())).thenReturn(List.of());

        // One active sensor alert: sensor 5 in warehouse 10 (center 1), WARNING.
        final EnvironmentAlert alert = new EnvironmentAlert();
        alert.setId(100L);
        alert.setSensorDeviceId(5L);
        alert.setSeverity(AlertSeverity.WARNING);
        alert.setMessage("냉동고-1: -10.0℃ (WARNING)");
        when(environmentAlertRepository.findByResolvedAtIsNullAndAcknowledgedFalse())
                .thenReturn(List.of(alert));

        final SensorDevice sensor = new SensorDevice();
        sensor.setId(5L);
        sensor.setWarehouseId(WAREHOUSE_ID);
        when(sensorDeviceRepository.findAllById(any())).thenReturn(List.of(sensor));

        final Center center = new Center();
        center.setId(CENTER_ID);
        final Warehouse warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setCenter(center);
        when(warehouseRepository.findAllById(any())).thenReturn(List.of(warehouse));

        // No pre-existing notifications; nothing stale to remove; empty unread read-back.
        when(notificationRepository.findByUserIdAndEventKey(anyLong(), any())).thenReturn(Optional.empty());
        when(notificationRepository.findByUserIdAndTypeIn(anyLong(), any())).thenReturn(List.of());
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(anyLong()))
                .thenReturn(List.of());
    }

    private boolean environmentAlertSaved() {
        service.getNotificationsForUser("u@example.com", false);
        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream()
                .anyMatch(n -> n.getType() == NotificationType.ENVIRONMENT_ALERT
                        && "ENV_ALERT:100".equals(n.getEventKey()));
    }

    @Test
    void warehouseManagerOfTheSensorWarehouseSeesTheAlert() {
        when(scopeAccessService.buildUserProfile(user)).thenReturn(profile(
                new ScopeAssignment(ScopeType.WAREHOUSE, CENTER_ID, WAREHOUSE_ID)));
        assertThat(environmentAlertSaved()).isTrue();
    }

    @Test
    void warehouseManagerOfAnotherWarehouseDoesNotSeeTheAlert() {
        when(scopeAccessService.buildUserProfile(user)).thenReturn(profile(
                new ScopeAssignment(ScopeType.WAREHOUSE, 2L, 99L)));
        assertThat(environmentAlertSaved()).isFalse();
    }

    @Test
    void centerManagerOfTheParentCenterSeesTheAlert() {
        when(scopeAccessService.buildUserProfile(user)).thenReturn(profile(
                new ScopeAssignment(ScopeType.CENTER, CENTER_ID, null)));
        assertThat(environmentAlertSaved()).isTrue();
    }

    @Test
    void centerManagerOfAnotherCenterDoesNotSeeTheAlert() {
        when(scopeAccessService.buildUserProfile(user)).thenReturn(profile(
                new ScopeAssignment(ScopeType.CENTER, 2L, null)));
        assertThat(environmentAlertSaved()).isFalse();
    }

    @Test
    void storeUserInTheSameWarehouseDoesNotSeeTheAlert() {
        // STORE assignment carries both the warehouse and center, which the flattened access sets
        // would treat as accessible; the type-based filter must still exclude it.
        when(scopeAccessService.buildUserProfile(user)).thenReturn(profile(
                new ScopeAssignment(ScopeType.STORE, CENTER_ID, WAREHOUSE_ID)));
        assertThat(environmentAlertSaved()).isFalse();
    }

    @Test
    void administratorSeesEveryAlert() {
        when(scopeAccessService.buildUserProfile(user)).thenReturn(
                new ScopeAccessProfile(true, List.of(ScopeAssignment.admin()), Set.of(), Set.of()));
        assertThat(environmentAlertSaved()).isTrue();
    }

    private ScopeAccessProfile profile(final ScopeAssignment assignment) {
        final Set<Long> centerIds = assignment.getCenterId() == null
                ? Set.of() : Set.of(assignment.getCenterId());
        final Set<Long> warehouseIds = assignment.getWarehouseId() == null
                ? Set.of() : Set.of(assignment.getWarehouseId());
        return new ScopeAccessProfile(false, List.of(assignment), centerIds, warehouseIds);
    }
}
