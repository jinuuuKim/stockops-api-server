package com.stockops.environment.ingestion;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.EnvironmentAlertNotification;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.SensorType;
import com.stockops.environment.cache.SensorReadingCacheService;
import com.stockops.environment.cache.SensorReadingSnapshot;
import com.stockops.repository.EnvironmentAlertNotificationRepository;
import com.stockops.repository.EnvironmentAlertRepository;
import com.stockops.repository.SensorDeviceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Processes live Sensimul telemetry into environment alert EVENTS.
 *
 * <p>Raw sensor measurements are never persisted in PostgreSQL — each normalized reading is
 * written to the shared Redis recent-reading cache so load-balanced API instances can serve
 * live values over REST. The DB only records threshold events: a {@code WARNING}/{@code CRITICAL}
 * status opens (or escalates) an active alert for the sensor, and a normal status auto-resolves it.
 * An alert stays active until the sensor normalizes or an administrator acknowledges it, which is
 * what drives the dashboard normal/warning/danger view.
 *
 * <p>Notifications are not sent inline: alert opens (and WARNING→CRITICAL escalations) insert a
 * PENDING outbox row in the same transaction, and the scheduled outbox sender delivers it. A
 * PostgreSQL partial unique index allows at most one active alert per sensor, so a concurrent
 * duplicate open from another instance fails the transaction and is dropped by the subscriber.
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
    private final EnvironmentAlertNotificationRepository alertNotificationRepository;
    private final SensorReadingCacheService sensorReadingCacheService;

    @Value("${stockops.environment.door-open-warning-duration:PT5M}")
    private Duration doorOpenWarningDuration = Duration.ofMinutes(5);

    /**
     * Creates the telemetry ingestion service.
     *
     * @param sensorDeviceRepository sensor device repository
     * @param environmentAlertRepository environment alert (event) repository
     * @param alertNotificationRepository alert notification outbox repository
     * @param sensorReadingCacheService shared recent reading cache
     */
    public TelemetryIngestionService(
            final SensorDeviceRepository sensorDeviceRepository,
            final EnvironmentAlertRepository environmentAlertRepository,
            final EnvironmentAlertNotificationRepository alertNotificationRepository,
            final SensorReadingCacheService sensorReadingCacheService) {
        this.sensorDeviceRepository = sensorDeviceRepository;
        this.environmentAlertRepository = environmentAlertRepository;
        this.alertNotificationRepository = alertNotificationRepository;
        this.sensorReadingCacheService = sensorReadingCacheService;
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
        final boolean doorSensor = isDoorSensor(device, payload);
        final List<SensorReadingSnapshot> recentReadings = doorSensor
                ? sensorReadingCacheService.readRecent(device.getId(), doorOpenWarningDuration.plusMinutes(1))
                : List.of();
        sensorReadingCacheService.append(toSnapshot(device, payload, recordedAt));

        if (doorSensor) {
            handleDoorReading(device, payload, recordedAt, recentReadings);
            return;
        }

        final AlertSeverity severity = resolveSeverity(device, payload);
        if (severity == null) {
            resolveActiveAlert(device, recordedAt);
        } else {
            openOrEscalateAlert(device, severity, payload);
        }
    }

    private void handleDoorReading(final SensorDevice device, final SensimulPayload payload,
                                   final Instant recordedAt,
                                   final List<SensorReadingSnapshot> recentReadings) {
        if (!isDoorOpen(payload)) {
            resolveActiveAlert(device, recordedAt);
            return;
        }

        final Instant openedAt = findCurrentOpenStartedAt(recentReadings, recordedAt);
        if (openedAt == null) {
            return;
        }

        final Duration openDuration = Duration.between(openedAt, recordedAt);
        if (openDuration.compareTo(doorOpenWarningDuration) >= 0) {
            openOrEscalateAlert(device, AlertSeverity.WARNING, "DOOR_OPEN_TOO_LONG",
                    String.format(Locale.ROOT, "%s: door open for %d seconds",
                            device.getName(), openDuration.toSeconds()));
        }
    }

    private Instant findCurrentOpenStartedAt(final List<SensorReadingSnapshot> recentReadings,
                                             final Instant recordedAt) {
        Instant openedAt = null;
        for (final SensorReadingSnapshot reading : recentReadings) {
            if (reading.recordedAt().isAfter(recordedAt)) {
                continue;
            }
            if (isDoorOpen(reading)) {
                if (openedAt == null) {
                    openedAt = reading.recordedAt();
                }
            } else {
                openedAt = null;
            }
        }
        return openedAt;
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
        final String unit = StringUtils.hasText(payload.unit()) ? payload.unit() : device.getUnit();
        openOrEscalateAlert(device, severity, resolveAlertType(payload), buildMessage(device, payload),
                payload.value(), unit);
    }

    private void openOrEscalateAlert(final SensorDevice device, final AlertSeverity severity,
                                     final String alertType, final String message) {
        openOrEscalateAlert(device, severity, alertType, message, null, null);
    }

    private void openOrEscalateAlert(final SensorDevice device, final AlertSeverity severity,
                                     final String alertType, final String message,
                                     final Double readingValue, final String readingUnit) {
        final Optional<EnvironmentAlert> active = environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(device.getId());

        if (active.isPresent()) {
            final EnvironmentAlert alert = active.get();
            final AlertSeverity previousSeverity = alert.getSeverity();
            if (previousSeverity != severity) {
                alert.setSeverity(severity);
                alert.setMessage(message);
                alert.setReadingValue(readingValue);
                alert.setReadingUnit(readingUnit);
                final EnvironmentAlert escalated = environmentAlertRepository.save(alert);
                LOGGER.debug("Updated active alert severity for sensorDeviceId={} to {}", device.getId(), severity);
                if (previousSeverity == AlertSeverity.WARNING && severity == AlertSeverity.CRITICAL) {
                    alertNotificationRepository.save(EnvironmentAlertNotification.pending(
                            escalated.getId(), EnvironmentAlertNotification.TriggerType.ESCALATED, severity));
                }
            }
            return;
        }

        final EnvironmentAlert alert = new EnvironmentAlert();
        alert.setSensorDeviceId(device.getId());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setReadingValue(readingValue);
        alert.setReadingUnit(readingUnit);
        alert.setAcknowledged(false);
        final EnvironmentAlert opened = environmentAlertRepository.save(alert);
        LOGGER.debug("Opened {} alert for sensorDeviceId={}", severity, device.getId());
        alertNotificationRepository.save(EnvironmentAlertNotification.pending(
                opened.getId(), EnvironmentAlertNotification.TriggerType.OPENED, severity));
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
     * Resolves the alert severity for a reading. Sensors with configured threshold bounds are
     * judged server-side by value (outside critical bounds → CRITICAL, outside warning bounds →
     * WARNING, inside → normal); sensors without bounds fall back to trusting the payload status.
     *
     * @param device sensor device (carries optional threshold bounds)
     * @param payload live payload
     * @return resolved severity, or {@code null} for normal states
     */
    private AlertSeverity resolveSeverity(final SensorDevice device, final SensimulPayload payload) {
        if (device.hasThresholds()) {
            return severityFromThresholds(device, payload.value());
        }
        return severityFor(payload.status());
    }

    private AlertSeverity severityFromThresholds(final SensorDevice device, final double value) {
        if (outOfBounds(value, device.getCritMin(), device.getCritMax())) {
            return AlertSeverity.CRITICAL;
        }
        if (outOfBounds(value, device.getWarnMin(), device.getWarnMax())) {
            return AlertSeverity.WARNING;
        }
        return null;
    }

    private boolean outOfBounds(final double value, final Double min, final Double max) {
        if (min != null && value < min) {
            return true;
        }
        return max != null && value > max;
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

    private SensorReadingSnapshot toSnapshot(final SensorDevice device, final SensimulPayload payload,
                                             final Instant recordedAt) {
        final String unit = StringUtils.hasText(payload.unit()) ? payload.unit() : device.getUnit();
        return new SensorReadingSnapshot(
                device.getId(),
                payload.siteId(),
                payload.sensorId(),
                payload.sensorType(),
                payload.valueKind(),
                payload.value(),
                unit,
                payload.status(),
                recordedAt,
                payload.sequenceId());
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

    private boolean isDoorSensor(final SensorDevice device, final SensimulPayload payload) {
        if (device.getSensorType() == SensorType.DOOR) {
            return true;
        }
        if ("door_open".equalsIgnoreCase(device.getSourceChannel())) {
            return true;
        }
        return StringUtils.hasText(payload.sensorType())
                && payload.sensorType().toLowerCase(Locale.ROOT).contains("door");
    }

    private boolean isDoorOpen(final SensimulPayload payload) {
        if (StringUtils.hasText(payload.status())) {
            final String normalized = payload.status().trim().toUpperCase(Locale.ROOT);
            if ("OPEN".equals(normalized) || "OPENED".equals(normalized)) {
                return true;
            }
            if ("CLOSED".equals(normalized) || "CLOSE".equals(normalized) || "OK".equals(normalized)) {
                return false;
            }
        }
        return payload.value() >= 0.5d;
    }

    private boolean isDoorOpen(final SensorReadingSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.status())) {
            final String normalized = snapshot.status().trim().toUpperCase(Locale.ROOT);
            if ("OPEN".equals(normalized) || "OPENED".equals(normalized)) {
                return true;
            }
            if ("CLOSED".equals(normalized) || "CLOSE".equals(normalized) || "OK".equals(normalized)) {
                return false;
            }
        }
        return snapshot.value() != null && snapshot.value() >= 0.5d;
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
