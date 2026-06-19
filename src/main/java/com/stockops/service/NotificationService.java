package com.stockops.service;

import com.stockops.dto.NotificationDTO;
import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.ExpiryAlert;
import com.stockops.entity.Inventory;
import com.stockops.entity.Location;
import com.stockops.entity.Notification;
import com.stockops.entity.NotificationType;
import com.stockops.entity.Product;
import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.PurchaseOrderStatus;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.User;
import com.stockops.entity.Warehouse;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.notification.lowstock.LowStockRule;
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
import com.stockops.config.MetricsConfig;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates operational events into user-facing in-app notifications.
 * Low-stock and expiry signals are synchronized on read to avoid stale badges,
 * while purchase order status notifications are emitted at transition time.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final ExpiryAlertRepository expiryAlertRepository;
    private final EnvironmentAlertRepository environmentAlertRepository;
    private final SensorDeviceRepository sensorDeviceRepository;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final MetricsConfig metricsConfig;

    /**
     * Returns notifications for the authenticated user.
     *
     * @param userEmail authenticated user email
     * @param includeRead whether read notifications should be included
     * @return notification list ordered by newest first
     */
    public List<NotificationDTO> getNotificationsForUser(final String userEmail, final boolean includeRead) {
        final User user = userService.getUserByEmail(userEmail);
        syncSystemNotifications(user);

        final List<Notification> notifications = includeRead
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                : notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId());

        return notifications.stream().map(this::toDto).toList();
    }

    /**
     * Counts unread notifications for the authenticated user.
     *
     * @param userEmail authenticated user email
     * @return unread notification count
     */
    public long countUnreadNotifications(final String userEmail) {
        final User user = userService.getUserByEmail(userEmail);
        syncSystemNotifications(user);
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    /**
     * Marks one notification as read.
     *
     * @param userEmail authenticated user email
     * @param notificationId notification identifier
     */
    public void markAsRead(final String userEmail, final Long notificationId) {
        final User user = userService.getUserByEmail(userEmail);
        final Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    /**
     * Marks all notifications for the authenticated user as read.
     *
     * @param userEmail authenticated user email
     */
    public void markAllAsRead(final String userEmail) {
        final User user = userService.getUserByEmail(userEmail);
        final List<Notification> notifications = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId());

        for (final Notification notification : notifications) {
            notification.setRead(true);
        }

        notificationRepository.saveAll(notifications);
    }

    /**
     * Creates a purchase order lifecycle notification for the requester when the status changes.
     * Duplicate notifications for the same purchase order/status combination are ignored.
     *
     * @param purchaseOrder purchase order whose status changed
     * @param status new purchase order status
     */
    public void createPurchaseOrderStatusNotification(final PurchaseOrder purchaseOrder,
                                                      final PurchaseOrderStatus status) {
        if (purchaseOrder.getRequestedBy() == null || purchaseOrder.getRequestedBy().getId() == null) {
            return;
        }

        final String eventKey = "PO_STATUS:" + purchaseOrder.getId() + ':' + status;
        if (notificationRepository.existsByUserIdAndEventKey(purchaseOrder.getRequestedBy().getId(), eventKey)) {
            return;
        }

        final Notification notification = new Notification();
        notification.setUserId(purchaseOrder.getRequestedBy().getId());
        notification.setType(NotificationType.PURCHASE_ORDER_STATUS_CHANGED);
        notification.setTitle("발주 상태 변경");
        notification.setMessage(String.format("발주 %s 상태가 %s(으)로 변경되었습니다.",
                purchaseOrder.getPoNumber(),
                formatPurchaseOrderStatus(status)));
        notification.setRead(false);
        notification.setEventKey(eventKey);

        notificationRepository.save(notification);
        metricsConfig.recordNotificationSent("in_app");
    }

    private void syncSystemNotifications(final User user) {
        syncLowStockNotifications(user);
        syncExpiryNotifications(user);
        syncEnvironmentAlertNotifications(user);
    }

    /**
     * Synchronizes environment (sensor) alerts into the user's in-app notification center.
     *
     * <p>Unlike low-stock/expiry, sensor alerts are <em>scoped</em>: a user only sees an alert when
     * their role grants visibility over the sensor's location — the manager of the sensor's
     * warehouse (WAREHOUSE scope), the manager of its parent center (CENTER scope), or an
     * administrator (ADMIN/global). STORE-scoped users never see sensor alerts. Only currently
     * active (unresolved and unacknowledged) alerts are shown; when an alert resolves or is
     * acknowledged it drops out of this set and the notification is removed on the next sync.
     *
     * @param user the user whose notifications are being synchronized
     */
    private void syncEnvironmentAlertNotifications(final User user) {
        final ScopeAccessProfile profile = scopeAccessService.buildUserProfile(user);

        final List<EnvironmentAlert> activeAlerts =
                environmentAlertRepository.findByResolvedAtIsNullAndAcknowledgedFalse();
        if (activeAlerts.isEmpty()) {
            removeInactiveNotifications(user.getId(), NotificationType.ENVIRONMENT_ALERT, Set.of());
            return;
        }

        // Batch-resolve sensor -> warehouse -> center to keep the per-user sync free of N+1 queries.
        final Map<Long, SensorDevice> sensorsById = sensorDeviceRepository.findAllById(activeAlerts.stream()
                        .map(EnvironmentAlert::getSensorDeviceId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(SensorDevice::getId, Function.identity()));
        final Map<Long, Warehouse> warehousesById = warehouseRepository.findAllById(sensorsById.values().stream()
                        .map(SensorDevice::getWarehouseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Warehouse::getId, Function.identity()));

        final Set<String> activeEventKeys = new HashSet<>();

        for (final EnvironmentAlert alert : activeAlerts) {
            final SensorDevice sensor = sensorsById.get(alert.getSensorDeviceId());
            final Long warehouseId = sensor == null ? null : sensor.getWarehouseId();
            final Warehouse warehouse = warehouseId == null ? null : warehousesById.get(warehouseId);
            final Long centerId = (warehouse == null || warehouse.getCenter() == null)
                    ? null : warehouse.getCenter().getId();

            if (!isEnvironmentAlertVisible(profile, warehouseId, centerId)) {
                continue;
            }

            final String eventKey = "ENV_ALERT:" + alert.getId();
            final String title = environmentAlertTitle(alert);
            final String message = environmentAlertMessage(alert);

            activeEventKeys.add(eventKey);
            upsertEnvironmentAlertNotification(user.getId(), eventKey, title, message);
        }

        removeInactiveNotifications(user.getId(), NotificationType.ENVIRONMENT_ALERT, activeEventKeys);
    }

    /**
     * Whether an environment alert for the given location is visible to the scope profile.
     * Matches on raw assignment <em>type</em> (not the flattened access sets, which would
     * over-grant a STORE user or let a warehouse manager see sibling warehouses in their center):
     * ADMIN/global, or a CENTER assignment on the sensor's center, or a WAREHOUSE assignment on the
     * sensor's warehouse. Sensors with no warehouse mapping are visible to administrators only.
     */
    private boolean isEnvironmentAlertVisible(final ScopeAccessProfile profile,
                                              final Long warehouseId,
                                              final Long centerId) {
        if (profile.global()) {
            return true;
        }
        if (warehouseId == null) {
            return false;
        }
        for (final ScopeAssignment assignment : profile.assignments()) {
            if (assignment.getScope() == ScopeType.WAREHOUSE
                    && warehouseId.equals(assignment.getWarehouseId())) {
                return true;
            }
            if (assignment.getScope() == ScopeType.CENTER
                    && centerId != null && centerId.equals(assignment.getCenterId())) {
                return true;
            }
        }
        return false;
    }

    private String environmentAlertTitle(final EnvironmentAlert alert) {
        return alert.getSeverity() == AlertSeverity.CRITICAL ? "환경 위험 경보" : "환경 경고";
    }

    private String environmentAlertMessage(final EnvironmentAlert alert) {
        return alert.getMessage() != null ? alert.getMessage() : "환경 센서 이상이 감지되었습니다.";
    }

    /**
     * Upserts a sensor-alert notification, re-marking it unread whenever its title or message
     * changes (e.g. a WARNING→CRITICAL escalation), so an escalation re-surfaces in the bell.
     */
    private void upsertEnvironmentAlertNotification(final Long userId,
                                                    final String eventKey,
                                                    final String title,
                                                    final String message) {
        final Optional<Notification> existing = notificationRepository.findByUserIdAndEventKey(userId, eventKey);
        final boolean isNew = existing.isEmpty();
        final Notification notification = existing.orElseGet(Notification::new);

        final boolean changed = isNew
                || !Objects.equals(notification.getTitle(), title)
                || !Objects.equals(notification.getMessage(), message);

        notification.setUserId(userId);
        notification.setType(NotificationType.ENVIRONMENT_ALERT);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setEventKey(eventKey);
        if (changed) {
            notification.setRead(false);
        }

        notificationRepository.save(notification);
        if (isNew) {
            metricsConfig.recordNotificationSent("in_app");
        }
    }

    private void syncLowStockNotifications(final User user) {
        final List<Inventory> inventories = inventoryRepository.findAll();
        final Map<Long, Product> productsById = productRepository.findAllById(inventories.stream()
                        .map(Inventory::getProductId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, Function.identity()));
        final Map<Long, Location> locationsById = locationRepository.findAllById(inventories.stream()
                        .map(Inventory::getLocationId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Location::getId, Function.identity()));

        final Set<String> activeEventKeys = new HashSet<>();

        for (final Inventory inventory : inventories) {
            final Product product = productsById.get(inventory.getProductId());
            if (!LowStockRule.isLow(inventory, product)) {
                continue;
            }
            final int availableQuantity = LowStockRule.availableQuantity(inventory);

            final Location location = locationsById.get(inventory.getLocationId());
            final String eventKey = "LOW_STOCK:" + inventory.getId();
            final String message = String.format("%s 재고가 안전재고 이하입니다. 현재 %d개 / 기준 %d개%s",
                    product.getName(),
                    availableQuantity,
                    product.getSafetyStockQuantity(),
                    location != null ? " (위치: " + location.getCode() + ')' : "");

            activeEventKeys.add(eventKey);
            upsertSystemNotification(user.getId(), NotificationType.LOW_STOCK, eventKey, "안전재고 부족", message);
        }

        removeInactiveNotifications(user.getId(), NotificationType.LOW_STOCK, activeEventKeys);
    }

    private void syncExpiryNotifications(final User user) {
        final List<ExpiryAlert> alerts = expiryAlertRepository.findByAcknowledgedFalse();
        final Map<Long, Product> productsById = productRepository.findAllById(alerts.stream()
                        .map(ExpiryAlert::getProductId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, Function.identity()));

        final Set<String> activeEventKeys = new HashSet<>();

        for (final ExpiryAlert alert : alerts) {
            final Product product = productsById.get(alert.getProductId());
            final String productName = product != null ? product.getName() : "상품";
            final String eventKey = "EXPIRY_ALERT:" + alert.getId();
            final String message = String.format("%s 유통기한이 %d일 남았습니다. 대상 수량 %d개",
                    productName,
                    alert.getDaysUntilExpiry(),
                    safeInt(alert.getQuantity()));

            activeEventKeys.add(eventKey);
            upsertSystemNotification(user.getId(), NotificationType.EXPIRY_APPROACHING, eventKey, "유통기한 임박", message);
        }

        removeInactiveNotifications(user.getId(), NotificationType.EXPIRY_APPROACHING, activeEventKeys);
    }

    private void upsertSystemNotification(final Long userId,
                                          final NotificationType type,
                                          final String eventKey,
                                          final String title,
                                          final String message) {
        final Optional<Notification> existing = notificationRepository.findByUserIdAndEventKey(userId, eventKey);
        final boolean isNew = existing.isEmpty();
        final Notification notification = existing.orElseGet(Notification::new);

        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setEventKey(eventKey);
        if (isNew) {
            notification.setRead(false);
        }

        notificationRepository.save(notification);
        if (isNew) {
            metricsConfig.recordNotificationSent("in_app");
        }
    }

    private void removeInactiveNotifications(final Long userId,
                                             final NotificationType type,
                                             final Set<String> activeEventKeys) {
        final List<Notification> notifications = notificationRepository.findByUserIdAndTypeIn(userId, EnumSet.of(type));
        final List<Notification> staleNotifications = notifications.stream()
                .filter(notification -> !activeEventKeys.contains(notification.getEventKey()))
                .toList();

        if (!staleNotifications.isEmpty()) {
            notificationRepository.deleteAll(staleNotifications);
        }
    }

    private int safeInt(final Integer value) {
        return value == null ? 0 : value;
    }

    private String formatPurchaseOrderStatus(final PurchaseOrderStatus status) {
        return switch (status) {
            case DRAFT -> "임시저장";
            case REQUESTED -> "요청됨";
            case ACCEPTED -> "수락됨";
            case PARTIALLY_ACCEPTED -> "부분 수락";
            case REJECTED -> "거절됨";
            case CANCELLED -> "취소됨";
            case SHIPMENT_CREATED -> "발송 등록";
            case INBOUND_PENDING -> "입고 대기";
            case COMPLETED -> "완료";
        };
    }

    private NotificationDTO toDto(final Notification notification) {
        return new NotificationDTO(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }

    public NotificationService(final NotificationRepository notificationRepository, final UserService userService, final InventoryRepository inventoryRepository, final ProductRepository productRepository, final LocationRepository locationRepository, final ExpiryAlertRepository expiryAlertRepository, final EnvironmentAlertRepository environmentAlertRepository, final SensorDeviceRepository sensorDeviceRepository, final WarehouseRepository warehouseRepository, final ScopeAccessService scopeAccessService, final MetricsConfig metricsConfig) {
        this.notificationRepository = notificationRepository;
        this.userService = userService;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.expiryAlertRepository = expiryAlertRepository;
        this.environmentAlertRepository = environmentAlertRepository;
        this.sensorDeviceRepository = sensorDeviceRepository;
        this.warehouseRepository = warehouseRepository;
        this.scopeAccessService = scopeAccessService;
        this.metricsConfig = metricsConfig;
    }
}
