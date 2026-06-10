package com.stockops.environment.ingestion;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.SensorDevice;
import com.stockops.environment.EnvironmentAlertNotifier;
import com.stockops.repository.EnvironmentAlertRepository;
import com.stockops.repository.SensorDeviceRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Processes live Sensimul telemetry into environment alert EVENTS.
 *
 * <p>Raw sensor measurements are no longer persisted in bulk — real-time values are viewed
 * client-side (admin-web subscribes to MQTT directly). The API only records threshold events:
 * a {@code WARNING}/{@code CRITICAL} status opens (or escalates) an active alert for the sensor,
 * and a normal status auto-resolves it. An alert stays active until the sensor normalizes or an
 * administrator acknowledges it, which is what drives the dashboard normal/warning/danger view.
 *
 * @author StockOps Team
 * @since 1.0
 * @see EnvironmentAlertRepository
 */
@Service
public class TelemetryIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final SensorDeviceRepository sensorDeviceRepository;
    private final EnvironmentAlertRepository environmentAlertRepository;
    private final EnvironmentAlertNotifier environmentAlertNotifier;

    /**
     * Creates the telemetry ingestion service.
     *
     * @param sensorDeviceRepository sensor device repository
     * @param environmentAlertRepository environment alert (event) repository
     * @param environmentAlertNotifier best-effort alert notifier (webhook/email)
     */
    public TelemetryIngestionService(
            final SensorDeviceRepository sensorDeviceRepository,
            final EnvironmentAlertRepository environmentAlertRepository,
            final EnvironmentAlertNotifier environmentAlertNotifier) {
        this.sensorDeviceRepository = sensorDeviceRepository;
        this.environmentAlertRepository = environmentAlertRepository;
        this.environmentAlertNotifier = environmentAlertNotifier;
    }

    /**
     * Ingests a single telemetry payload, recording only state-change events.
     * Invalid or unmapped payloads are logged and skipped without failing the subscriber.
     *
     * @param payload live sensor payload
     */
    @Transactional
    public void ingest(final SensimulPayload payload) {
        if (!isPayloadValid(payload)) {
            LOGGER.warn("Skipping malformed telemetry payload: {}", payload);
            return;
        }

        final Instant recordedAt = parseRecordedAt(payload.timestamp());
        if (recordedAt == null) {
            LOGGER.warn("Skipping telemetry payload with invalid timestamp: {}", payload.timestamp());
            return;
        }

        final String mqttTopic = SensimulTopics.liveSensorTopic(payload.siteId(), payload.sensorId());
        final Optional<SensorDevice> sensorDevice = sensorDeviceRepository.findByMqttTopic(mqttTopic);
        if (sensorDevice.isEmpty()) {
            LOGGER.warn("Skipping telemetry for unknown or deleted sensor topic: {}", mqttTopic);
            return;
        }

        final SensorDevice device = sensorDevice.get();
        final AlertSeverity severity = severityFor(payload.status());
        if (severity == null) {
            resolveActiveAlert(device, recordedAt);
        } else {
            openOrEscalateAlert(device, severity, payload);
        }
    }

    /**
     * Opens a new active alert for a sensor, or escalates the severity of the existing one.
     *
     * @param device sensor device
     * @param severity event severity derived from the reported status
     * @param payload live payload
     */
    private void openOrEscalateAlert(final SensorDevice device, final AlertSeverity severity,
                                     final SensimulPayload payload) {
        final Optional<EnvironmentAlert> active = environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(device.getId());
        final String message = buildMessage(device, payload);

        if (active.isPresent()) {
            final EnvironmentAlert alert = active.get();
            if (alert.getSeverity() != severity) {
                alert.setSeverity(severity);
                alert.setMessage(message);
                final EnvironmentAlert escalated = environmentAlertRepository.save(alert);
                LOGGER.debug("Updated active alert severity for sensorDeviceId={} to {}", device.getId(), severity);
                environmentAlertNotifier.notifyAlertOpened(escalated, device);
            }
            return;
        }

        final EnvironmentAlert alert = new EnvironmentAlert();
        alert.setSensorDeviceId(device.getId());
        alert.setAlertType(resolveAlertType(payload));
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setAcknowledged(false);
        final EnvironmentAlert opened = environmentAlertRepository.save(alert);
        LOGGER.debug("Opened {} alert for sensorDeviceId={}", severity, device.getId());
        environmentAlertNotifier.notifyAlertOpened(opened, device);
    }

    /**
     * Auto-resolves the sensor's active alert when a normal reading arrives.
     *
     * @param device sensor device
     * @param resolvedAt resolution timestamp
     */
    private void resolveActiveAlert(final SensorDevice device, final Instant resolvedAt) {
        environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(device.getId())
                .ifPresent(alert -> {
                    alert.setResolvedAt(resolvedAt);
                    environmentAlertRepository.save(alert);
                    LOGGER.debug("Auto-resolved active alert for sensorDeviceId={}", device.getId());
                });
    }

    /**
     * Maps a reported sensor status to an alert severity, or {@code null} when the status is normal.
     *
     * @param status reported status string
     * @return mapped severity, or {@code null} for normal/unknown states
     */
    private AlertSeverity severityFor(final String status) {
        if (status == null) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL", "DANGER" -> AlertSeverity.CRITICAL;
            case "WARNING", "WARN" -> AlertSeverity.WARNING;
            default -> null;
        };
    }

    private String resolveAlertType(final SensimulPayload payload) {
        if (StringUtils.hasText(payload.valueKind())) {
            return payload.valueKind();
        }
        return StringUtils.hasText(payload.sensorType()) ? payload.sensorType() : "SENSOR";
    }

    private String buildMessage(final SensorDevice device, final SensimulPayload payload) {
        final String unit = StringUtils.hasText(payload.unit()) ? payload.unit() : device.getUnit();
        return String.format(Locale.ROOT, "%s: %s%s (%s)",
                device.getName(),
                payload.value(),
                StringUtils.hasText(unit) ? unit : "",
                payload.status());
    }

    private boolean isPayloadValid(final SensimulPayload payload) {
        if (payload == null) {
            return false;
        }
        if (!StringUtils.hasText(payload.siteId())) {
            return false;
        }
        if (!StringUtils.hasText(payload.sensorId())) {
            return false;
        }
        if (!StringUtils.hasText(payload.status())) {
            return false;
        }
        return StringUtils.hasText(payload.timestamp());
    }

    private Instant parseRecordedAt(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
