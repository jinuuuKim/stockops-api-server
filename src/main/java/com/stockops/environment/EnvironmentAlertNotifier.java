package com.stockops.environment;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.Warehouse;
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

    /**
     * Creates the notifier.
     *
     * @param webhookService webhook dispatch service
     * @param webhookEndpointConfigRepository enabled webhook endpoint lookup
     * @param warehouseRepository warehouse repository (resolves the sensor's warehouse name for the payload)
     */
    public EnvironmentAlertNotifier(
            final WebhookService webhookService,
            final WebhookEndpointConfigRepository webhookEndpointConfigRepository,
            final WarehouseRepository warehouseRepository) {
        this.webhookService = webhookService;
        this.webhookEndpointConfigRepository = webhookEndpointConfigRepository;
        this.warehouseRepository = warehouseRepository;
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

        final WebhookPayload payload = WebhookPayload.builder()
                .eventType(EVENT_TYPE)
                .message(alert.getMessage())
                .severity(toWebhookSeverity(alert.getSeverity()))
                .location(warehouse.getName())
                .alertType(alert.getAlertType())
                .timestamp(Instant.now())
                .build();
        for (final WebhookEndpointConfig endpoint : endpoints.values()) {
            webhookService.send(endpoint.getProviderType().name(), endpoint.getWebhookUrl(), payload);
        }
    }

    private WebhookPayload.Severity toWebhookSeverity(final AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> WebhookPayload.Severity.CRITICAL;
            case WARNING -> WebhookPayload.Severity.WARNING;
            default -> WebhookPayload.Severity.INFO;
        };
    }
}
