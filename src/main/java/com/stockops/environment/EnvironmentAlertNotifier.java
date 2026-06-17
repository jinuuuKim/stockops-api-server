package com.stockops.environment;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.Warehouse;
import com.stockops.notification.guidance.EventGuidanceService;
import com.stockops.notification.webhook.AlertTypeNotificationCatalog;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import com.stockops.repository.WarehouseRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Delivers environment alert notifications via webhook.
 *
 * <p>Called by the outbox sender, not by telemetry ingestion: ingestion only records
 * notification rows, and the scheduled {@code EnvironmentAlertNotificationSender} claims and
 * delivers them. Failures propagate to the sender so the outbox can retry.
 *
 * @author StockOps Team
 * @since 2.2
 */
@Service
public class EnvironmentAlertNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentAlertNotifier.class);
    private static final String EVENT_TYPE = "ENVIRONMENT_ALERT";

    private final WebhookService webhookService;
    private final WebhookEndpointConfigRepository webhookEndpointConfigRepository;
    private final WarehouseRepository warehouseRepository;
    private final EventGuidanceService eventGuidanceService;

    /**
     * Creates the notifier.
     *
     * @param webhookService webhook dispatch service
     * @param webhookEndpointConfigRepository enabled webhook endpoint lookup
     * @param warehouseRepository warehouse repository (resolves the sensor's warehouse name for the payload)
     * @param eventGuidanceService produces the Korean remediation guidance shown in the card
     */
    public EnvironmentAlertNotifier(
            final WebhookService webhookService,
            final WebhookEndpointConfigRepository webhookEndpointConfigRepository,
            final WarehouseRepository warehouseRepository,
            final EventGuidanceService eventGuidanceService) {
        this.webhookService = webhookService;
        this.webhookEndpointConfigRepository = webhookEndpointConfigRepository;
        this.warehouseRepository = warehouseRepository;
        this.eventGuidanceService = eventGuidanceService;
    }

    /**
     * Delivers the alert to the webhook endpoints scoped to the sensor's warehouse and its
     * center. Warehouse-specific endpoints and center-level (warehouse-unscoped) endpoints both
     * receive the event; everything else is skipped — this is targeted delivery, not a broadcast.
     * Failures propagate so the outbox sender can retry (delivery is at-least-once).
     *
     * @param alert the opened/escalated alert
     * @param device the related sensor device (may be null)
     */
    public void dispatch(final EnvironmentAlert alert, final SensorDevice device) {
        LOGGER.debug("Dispatching environment alert notification for alertId={}", alert.getId());
        dispatchWebhooks(alert, device);
    }

    private void dispatchWebhooks(final EnvironmentAlert alert, final SensorDevice device) {
        final Long warehouseId = device == null ? null : device.getWarehouseId();
        if (warehouseId == null) {
            LOGGER.warn("Alert id={} comes from a sensor with no warehouse; skipping scoped webhook dispatch",
                    alert.getId());
            return;
        }
        final Warehouse warehouse = warehouseRepository.findByIdWithCenter(warehouseId).orElse(null);
        if (warehouse == null) {
            LOGGER.warn("Warehouse id={} for alert id={} not found; skipping webhook dispatch",
                    warehouseId, alert.getId());
            return;
        }
        final Long centerId = warehouse.getCenter() == null ? null : warehouse.getCenter().getId();

        // Warehouse-specific endpoints + the center's center-level (warehouse-unscoped) endpoints,
        // de-duplicated by id.
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
        if (endpoints.isEmpty()) {
            LOGGER.debug("No enabled webhook endpoint for warehouse={} / center={} (alert id={}); nothing dispatched",
                    warehouseId, centerId, alert.getId());
            return;
        }

        final AlertTypeNotificationCatalog.Mapping mapping =
                AlertTypeNotificationCatalog.forAlertType(alert.getAlertType());
        final EventGuidanceService.EventGuidance guidance =
                eventGuidanceService.guidanceFor(alert.getAlertType(), alert.getSeverity().name());
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType(EVENT_TYPE)
                .message(alert.getMessage())
                .severity(toWebhookSeverity(alert.getSeverity()))
                .location(warehouse.getName())
                .alertType(alert.getAlertType())
                .timestamp(Instant.now())
                .permissionLabel(AlertTypeNotificationCatalog.roleLabelKo(mapping.baseRole()))
                .alertName(mapping.alertName())
                .eventTitle(buildEventTitle(warehouse.getName(), device, mapping.alertName()))
                .configuredValue(buildConfiguredValue(device))
                .currentValue(buildCurrentValue(alert, device))
                .statusLabel(buildStatusLabel(alert, device))
                .guidance(guidance == null ? null : guidance.text())
                .guidanceSource(buildGuidanceSource(guidance))
                .build();
        for (final WebhookEndpointConfig endpoint : endpoints.values()) {
            webhookService.send(endpoint.getProviderType().name(), endpoint.getWebhookUrl(), payload);
        }
    }

    private String buildEventTitle(final String warehouseName, final SensorDevice device,
                                   final String alertName) {
        final StringBuilder sb = new StringBuilder("[").append(warehouseName).append("] ");
        if (device != null && device.getName() != null) {
            sb.append(device.getName()).append(" · ");
        }
        return sb.append(alertName).toString();
    }

    /**
     * Builds the "설정값" line from the sensor's critical thresholds, e.g. {@code "허용 -30.0 ~ -12.0°C"}.
     * Returns null when the device has no thresholds (the fact is then omitted from the card).
     */
    private String buildConfiguredValue(final SensorDevice device) {
        if (device == null || !device.hasThresholds()) {
            return null;
        }
        final String unit = device.getUnit() == null ? "" : device.getUnit();
        final Double min = device.getCritMin();
        final Double max = device.getCritMax();
        if (min != null && max != null) {
            return String.format(java.util.Locale.ROOT, "허용 %s ~ %s%s", min, max, unit);
        }
        if (max != null) {
            return String.format(java.util.Locale.ROOT, "허용 최대 %s%s", max, unit);
        }
        if (min != null) {
            return String.format(java.util.Locale.ROOT, "허용 최소 %s%s", min, unit);
        }
        return null;
    }

    /** Builds the "현재 값" line from the alert's persisted reading value, or null when absent. */
    private String buildCurrentValue(final EnvironmentAlert alert, final SensorDevice device) {
        if (alert.getReadingValue() == null) {
            return null;
        }
        String unit = alert.getReadingUnit();
        if (unit == null && device != null) {
            unit = device.getUnit();
        }
        return alert.getReadingValue() + (unit == null ? "" : unit);
    }

    /** Source string for the delivery log, e.g. "AI_FORMATTED", "KNOWLEDGE_BASE:s3://…", "FALLBACK". */
    private String buildGuidanceSource(final EventGuidanceService.EventGuidance guidance) {
        if (guidance == null) {
            return null;
        }
        return guidance.citation() == null
                ? guidance.source().name()
                : guidance.source().name() + ":" + guidance.citation();
    }

    /** Derives the "상태" label (초과/미달/정상) by comparing the reading to the sensor's bounds. */
    private String buildStatusLabel(final EnvironmentAlert alert, final SensorDevice device) {
        final Double value = alert.getReadingValue();
        if (value == null || device == null || !device.hasThresholds()) {
            return null;
        }
        if ((device.getCritMax() != null && value > device.getCritMax())
                || (device.getWarnMax() != null && value > device.getWarnMax())) {
            return "초과";
        }
        if ((device.getCritMin() != null && value < device.getCritMin())
                || (device.getWarnMin() != null && value < device.getWarnMin())) {
            return "미달";
        }
        return "정상";
    }

    private WebhookPayload.Severity toWebhookSeverity(final AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> WebhookPayload.Severity.CRITICAL;
            case WARNING -> WebhookPayload.Severity.WARNING;
            default -> WebhookPayload.Severity.INFO;
        };
    }
}
