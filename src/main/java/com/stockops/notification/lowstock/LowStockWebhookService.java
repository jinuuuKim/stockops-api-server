package com.stockops.notification.lowstock;

import com.stockops.entity.Center;
import com.stockops.entity.Inventory;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.entity.Warehouse;
import com.stockops.notification.role.RoleWebhookConfig;
import com.stockops.notification.role.RoleWebhookConfigRepository;
import com.stockops.notification.webhook.AlertTypeNotificationCatalog;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.WarehouseRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scans inventory for low stock and pushes Teams cards in addition to the in-app NotificationBell.
 *
 * <p>Routing mirrors the established environment-alert pattern:
 * <ul>
 *   <li><b>Per warehouse</b> — a warehouse with any low SKU notifies the webhook channels scoped to
 *       that warehouse plus its center's center-level channels. Those are the channels the
 *       창고관리자/센터관리자 of that warehouse watch (the schema has no per-user webhook; the
 *       warehouse/center-scoped endpoint is the realization of "그 창고 담당 관리자 채널").</li>
 *   <li><b>System-wide</b> — when the share of safety-stock-tracked SKUs that are low reaches
 *       {@code adminLowRatio}, the {@code adminRole} (최고관리자) role channels are also notified.</li>
 * </ul>
 *
 * <p>Frequency is bounded by a per-scope cooldown ({@link LowStockAlertState}); the in-app bell stays
 * real-time and is untouched. Low-stock detection reuses {@link LowStockRule} so it cannot drift from
 * the bell. Send failures are swallowed by {@link WebhookService}; the cooldown is still consumed so a
 * persistently failing endpoint does not turn into a per-scan retry storm.
 *
 * @author StockOps Team
 * @since 2.5
 */
@Service
public class LowStockWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LowStockWebhookService.class);
    private static final String EVENT_TYPE = "INVENTORY_LOW";
    private static final String ALERT_TYPE = "LOW_STOCK";

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final WebhookEndpointConfigRepository webhookEndpointConfigRepository;
    private final RoleWebhookConfigRepository roleWebhookConfigRepository;
    private final WebhookService webhookService;
    private final LowStockAlertStateRepository stateRepository;
    private final LowStockProperties properties;

    public LowStockWebhookService(
            final InventoryRepository inventoryRepository,
            final ProductRepository productRepository,
            final LocationRepository locationRepository,
            final WarehouseRepository warehouseRepository,
            final WebhookEndpointConfigRepository webhookEndpointConfigRepository,
            final RoleWebhookConfigRepository roleWebhookConfigRepository,
            final WebhookService webhookService,
            final LowStockAlertStateRepository stateRepository,
            final LowStockProperties properties) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.warehouseRepository = warehouseRepository;
        this.webhookEndpointConfigRepository = webhookEndpointConfigRepository;
        this.roleWebhookConfigRepository = roleWebhookConfigRepository;
        this.webhookService = webhookService;
        this.stateRepository = stateRepository;
        this.properties = properties;
    }

    /** One low-stock SKU at a warehouse, captured for card rendering. */
    private record LowItem(String productName, int available, int safetyStock) {
    }

    /**
     * Scans all inventory once and dispatches any due low-stock webhook cards.
     */
    @Transactional
    public void scanAndNotify() {
        if (!properties.isEnabled()) {
            return;
        }
        final List<Inventory> inventories = inventoryRepository.findAll();
        if (inventories.isEmpty()) {
            return;
        }

        final Map<Long, Product> productsById = productRepository.findAllById(inventories.stream()
                        .map(Inventory::getProductId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        final Map<Long, Location> locationsById = locationRepository.findAllById(inventories.stream()
                        .map(Inventory::getLocationId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));

        // Group low items by warehouse, and accumulate distinct-SKU counts for the global trigger
        // from the SAME scan so the two never diverge on what "low" means.
        final Map<Long, List<LowItem>> lowByWarehouse = new LinkedHashMap<>();
        final Set<Long> trackedSkus = new HashSet<>();
        final Set<Long> lowSkus = new HashSet<>();

        for (final Inventory inventory : inventories) {
            final Product product = productsById.get(inventory.getProductId());
            if (!LowStockRule.isTracked(product)) {
                continue;
            }
            trackedSkus.add(product.getId());
            if (!LowStockRule.isLow(inventory, product)) {
                continue;
            }
            lowSkus.add(product.getId());

            final Location location = locationsById.get(inventory.getLocationId());
            final Long warehouseId = location != null && location.getWarehouse() != null
                    ? location.getWarehouse().getId() : null;
            if (warehouseId == null) {
                // Unlocated stock can't be scoped to a warehouse channel; the global escalation
                // still accounts for it via lowSkus.
                continue;
            }
            lowByWarehouse.computeIfAbsent(warehouseId, key -> new ArrayList<>())
                    .add(new LowItem(product.getName(),
                            LowStockRule.availableQuantity(inventory), product.getSafetyStockQuantity()));
        }

        final Instant now = Instant.now();
        final Map<Long, Warehouse> warehousesById = warehouseRepository.findAllWithCenter().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        dispatchWarehouseCards(lowByWarehouse, warehousesById, now);
        dispatchGlobalCard(lowSkus.size(), trackedSkus.size(), lowByWarehouse, warehousesById, now);
    }

    private void dispatchWarehouseCards(final Map<Long, List<LowItem>> lowByWarehouse,
                                        final Map<Long, Warehouse> warehousesById, final Instant now) {
        for (final Map.Entry<Long, List<LowItem>> entry : lowByWarehouse.entrySet()) {
            final Long warehouseId = entry.getKey();
            final List<LowItem> items = entry.getValue();
            final Warehouse warehouse = warehousesById.get(warehouseId);
            if (warehouse == null) {
                continue;
            }
            final String scopeKey = "WAREHOUSE:" + warehouseId;
            if (!cooldownElapsed(scopeKey, now)) {
                continue;
            }

            final Long centerId = warehouse.getCenter() == null ? null : warehouse.getCenter().getId();
            final List<WebhookEndpointConfig> endpoints = resolveScopedEndpoints(warehouseId, centerId);
            if (endpoints.isEmpty()) {
                LOGGER.debug("No webhook endpoint for warehouse={} / center={}; skipping low-stock card",
                        warehouseId, centerId);
                continue;
            }

            final WebhookPayload payload = buildWarehousePayload(warehouse, items, now);
            for (final WebhookEndpointConfig endpoint : endpoints) {
                webhookService.send(endpoint.getProviderType().name(), endpoint.getWebhookUrl(), payload);
            }
            touchState(scopeKey, now, items.size());
            LOGGER.info("Low-stock card dispatched for warehouse={} ({} SKU, {} endpoints)",
                    warehouseId, items.size(), endpoints.size());
        }
    }

    private void dispatchGlobalCard(final int lowSkuCount, final int trackedSkuCount,
                                    final Map<Long, List<LowItem>> lowByWarehouse,
                                    final Map<Long, Warehouse> warehousesById, final Instant now) {
        if (trackedSkuCount <= 0 || lowSkuCount <= 0) {
            return;
        }
        final int threshold = (int) Math.ceil(trackedSkuCount * properties.getAdminLowRatio());
        if (lowSkuCount < threshold) {
            return;
        }
        final String scopeKey = "GLOBAL";
        if (!cooldownElapsed(scopeKey, now)) {
            return;
        }
        final List<RoleWebhookConfig> configs = roleWebhookConfigRepository
                .findByRoleInAndEnabledTrue(List.of(properties.getAdminRole()));
        if (configs.isEmpty()) {
            LOGGER.debug("No role webhook channel for role={}; skipping system-wide low-stock card",
                    properties.getAdminRole());
            return;
        }

        final WebhookPayload payload = buildGlobalPayload(lowSkuCount, trackedSkuCount, lowByWarehouse,
                warehousesById, now);
        final Set<String> sentUrls = new HashSet<>();
        for (final RoleWebhookConfig config : configs) {
            if (sentUrls.add(config.getWebhookUrl())) {
                webhookService.send(config.getProviderType().name(), config.getWebhookUrl(), payload);
            }
        }
        touchState(scopeKey, now, lowSkuCount);
        LOGGER.info("System-wide low-stock card dispatched: {}/{} SKU low ({} channels)",
                lowSkuCount, trackedSkuCount, sentUrls.size());
    }

    private List<WebhookEndpointConfig> resolveScopedEndpoints(final Long warehouseId, final Long centerId) {
        final Map<Long, WebhookEndpointConfig> endpoints = new LinkedHashMap<>();
        for (final WebhookEndpointConfig endpoint
                : webhookEndpointConfigRepository.findByWarehouseIdAndEnabledTrue(warehouseId)) {
            endpoints.put(endpoint.getId(), endpoint);
        }
        if (centerId != null) {
            for (final WebhookEndpointConfig endpoint
                    : webhookEndpointConfigRepository.findByCenterIdAndWarehouseIdIsNullAndEnabledTrue(centerId)) {
                endpoints.put(endpoint.getId(), endpoint);
            }
        }
        return new ArrayList<>(endpoints.values());
    }

    private WebhookPayload buildWarehousePayload(final Warehouse warehouse, final List<LowItem> items,
                                                 final Instant now) {
        final Center center = warehouse.getCenter();
        final boolean anyOut = items.stream().anyMatch(item -> item.available() == 0);
        final AlertTypeNotificationCatalog.Mapping mapping =
                AlertTypeNotificationCatalog.forAlertType(ALERT_TYPE);

        final Map<String, String> details = new LinkedHashMap<>();
        details.put("저재고 품목 수", items.size() + "개");
        final int limit = Math.max(1, properties.getMaxItemsPerCard());
        for (int i = 0; i < items.size() && i < limit; i++) {
            final LowItem item = items.get(i);
            details.put(item.productName(),
                    String.format("현재 %d개 / 안전재고 %d개", item.available(), item.safetyStock()));
        }
        if (items.size() > limit) {
            details.put("기타", "외 " + (items.size() - limit) + "개 품목");
        }

        return WebhookPayload.builder()
                .eventType(EVENT_TYPE)
                .alertType(ALERT_TYPE)
                .severity(anyOut ? WebhookPayload.Severity.CRITICAL : WebhookPayload.Severity.WARNING)
                .message(buildWarehouseMessage(warehouse, items))
                .location(warehouse.getName())
                .warehouseName(warehouse.getName())
                .centerName(center == null ? null : center.getName())
                .timestamp(now)
                .permissionLabel(AlertTypeNotificationCatalog.roleLabelKo(mapping.baseRole()))
                .alertName(mapping.alertName())
                .eventTitle(String.format("[%s] 저재고 %d건", warehouse.getName(), items.size()))
                .statusLabel("안전재고 미달")
                .details(details)
                .build();
    }

    private WebhookPayload buildGlobalPayload(final int lowSkuCount, final int trackedSkuCount,
                                              final Map<Long, List<LowItem>> lowByWarehouse,
                                              final Map<Long, Warehouse> warehousesById, final Instant now) {
        final int pct = (int) Math.round(lowSkuCount * 100.0 / trackedSkuCount);

        final Map<String, String> details = new LinkedHashMap<>();
        details.put("저재고 품목 비율", String.format("%d / %d개 (%d%%)", lowSkuCount, trackedSkuCount, pct));
        final int limit = Math.max(1, properties.getMaxItemsPerCard());
        int shown = 0;
        for (final Map.Entry<Long, List<LowItem>> entry : lowByWarehouse.entrySet()) {
            if (shown >= limit) {
                break;
            }
            final Warehouse warehouse = warehousesById.get(entry.getKey());
            final String name = warehouse == null ? ("창고 " + entry.getKey()) : warehouse.getName();
            details.put(name, entry.getValue().size() + "건");
            shown++;
        }

        return WebhookPayload.builder()
                .eventType(EVENT_TYPE)
                .alertType(ALERT_TYPE)
                .severity(WebhookPayload.Severity.CRITICAL)
                .message(String.format("전사 저재고 경보: 안전재고 미만 품목 %d / %d개 (%d%%)",
                        lowSkuCount, trackedSkuCount, pct))
                .timestamp(now)
                .permissionLabel(AlertTypeNotificationCatalog.roleLabelKo(properties.getAdminRole()))
                .alertName("전사 저재고 경보")
                .eventTitle(String.format("전사 저재고 경보 — 안전재고 미만 %d / %d개 품목 (%d%%)",
                        lowSkuCount, trackedSkuCount, pct))
                .statusLabel("안전재고 미달")
                .details(details)
                .build();
    }

    private String buildWarehouseMessage(final Warehouse warehouse, final List<LowItem> items) {
        final StringBuilder sb = new StringBuilder()
                .append('[').append(warehouse.getName()).append("] 저재고 ").append(items.size()).append("건");
        final int limit = Math.max(1, properties.getMaxItemsPerCard());
        for (int i = 0; i < items.size() && i < limit; i++) {
            final LowItem item = items.get(i);
            sb.append("\n- ").append(item.productName())
                    .append(String.format(" 현재 %d개 / 안전재고 %d개", item.available(), item.safetyStock()));
        }
        if (items.size() > limit) {
            sb.append("\n- 외 ").append(items.size() - limit).append("개 품목");
        }
        return sb.toString();
    }

    private boolean cooldownElapsed(final String scopeKey, final Instant now) {
        return stateRepository.findByScopeKey(scopeKey)
                .map(state -> now.isAfter(state.getLastNotifiedAt().plus(properties.getCooldown())))
                .orElse(true);
    }

    private void touchState(final String scopeKey, final Instant now, final int lowCount) {
        final LowStockAlertState state = stateRepository.findByScopeKey(scopeKey)
                .orElseGet(() -> new LowStockAlertState(scopeKey, now, lowCount));
        state.setLastNotifiedAt(now);
        state.setLastLowCount(lowCount);
        stateRepository.save(state);
    }
}
