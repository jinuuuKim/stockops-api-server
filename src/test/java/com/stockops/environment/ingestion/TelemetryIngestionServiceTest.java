package com.stockops.environment.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.SensorDevice;
import com.stockops.environment.EnvironmentAlertNotifier;
import com.stockops.repository.EnvironmentAlertRepository;
import com.stockops.repository.SensorDeviceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TelemetryIngestionService} event lifecycle.
 *
 * @author StockOps Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    private static final String TOPIC = "sensimul/sites/site-a/sensors/sensor-01";

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private EnvironmentAlertRepository environmentAlertRepository;

    @Mock
    private EnvironmentAlertNotifier environmentAlertNotifier;

    @InjectMocks
    private TelemetryIngestionService telemetryIngestionService;

    /**
     * Verifies a WARNING status opens a new active alert when none exists for the sensor.
     */
    @Test
    void ingestOpensWarningAlertWhenNoActiveAlert() {
        final SensorDevice device = sensorDevice(5L, "Temp-1", "C");
        when(sensorDeviceRepository.findByMqttTopic(TOPIC)).thenReturn(Optional.of(device));
        when(environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.empty());

        telemetryIngestionService.ingest(payload("WARNING", "2026-04-05T00:00:00Z"));

        final ArgumentCaptor<EnvironmentAlert> captor = ArgumentCaptor.forClass(EnvironmentAlert.class);
        verify(environmentAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getSensorDeviceId()).isEqualTo(5L);
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(captor.getValue().isAcknowledged()).isFalse();
        assertThat(captor.getValue().getResolvedAt()).isNull();
        verify(environmentAlertNotifier).notifyAlertOpened(any(), any());
    }

    /**
     * Verifies an escalation updates the active alert severity from WARNING to CRITICAL.
     */
    @Test
    void ingestEscalatesActiveAlertSeverity() {
        final SensorDevice device = sensorDevice(5L, "Temp-1", "C");
        final EnvironmentAlert active = activeAlert(5L, AlertSeverity.WARNING);
        when(sensorDeviceRepository.findByMqttTopic(TOPIC)).thenReturn(Optional.of(device));
        when(environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(active));

        telemetryIngestionService.ingest(payload("CRITICAL", "2026-04-05T00:00:00Z"));

        final ArgumentCaptor<EnvironmentAlert> captor = ArgumentCaptor.forClass(EnvironmentAlert.class);
        verify(environmentAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        verify(environmentAlertNotifier).notifyAlertOpened(any(), any());
    }

    /**
     * Verifies a repeated same-severity event does not create or rewrite an alert.
     */
    @Test
    void ingestDoesNotDuplicateActiveAlertOfSameSeverity() {
        final SensorDevice device = sensorDevice(5L, "Temp-1", "C");
        when(sensorDeviceRepository.findByMqttTopic(TOPIC)).thenReturn(Optional.of(device));
        when(environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(activeAlert(5L, AlertSeverity.WARNING)));

        telemetryIngestionService.ingest(payload("WARNING", "2026-04-05T00:00:00Z"));

        verify(environmentAlertRepository, never()).save(any());
    }

    /**
     * Verifies a normal status auto-resolves the sensor's active alert.
     */
    @Test
    void ingestResolvesActiveAlertOnNormalStatus() {
        final SensorDevice device = sensorDevice(5L, "Temp-1", "C");
        final EnvironmentAlert active = activeAlert(5L, AlertSeverity.WARNING);
        when(sensorDeviceRepository.findByMqttTopic(TOPIC)).thenReturn(Optional.of(device));
        when(environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(active));

        telemetryIngestionService.ingest(payload("ok", "2026-04-05T00:00:00Z"));

        final ArgumentCaptor<EnvironmentAlert> captor = ArgumentCaptor.forClass(EnvironmentAlert.class);
        verify(environmentAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
        verify(environmentAlertNotifier, never()).notifyAlertOpened(any(), any());
    }

    /**
     * Verifies a normal status with no active alert performs no writes.
     */
    @Test
    void ingestNormalStatusWithoutActiveAlertDoesNothing() {
        final SensorDevice device = sensorDevice(5L, "Temp-1", "C");
        when(sensorDeviceRepository.findByMqttTopic(TOPIC)).thenReturn(Optional.of(device));
        when(environmentAlertRepository
                .findFirstBySensorDeviceIdAndResolvedAtIsNullAndAcknowledgedFalseOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.empty());

        telemetryIngestionService.ingest(payload("ok", "2026-04-05T00:00:00Z"));

        verify(environmentAlertRepository, never()).save(any());
    }

    /**
     * Verifies malformed payloads are ignored without repository access.
     */
    @Test
    void ingestSkipsMalformedPayload() {
        telemetryIngestionService.ingest(new SensimulPayload(
                "", "sensor-01", "temperature", "temperature", 12.5, "C", "WARNING",
                "2026-04-05T00:00:00Z", 10L, "1.0"));

        verify(sensorDeviceRepository, never()).findByMqttTopic(any());
        verify(environmentAlertRepository, never()).save(any());
    }

    /**
     * Verifies invalid timestamps are dropped before repository access.
     */
    @Test
    void ingestSkipsInvalidTimestamp() {
        telemetryIngestionService.ingest(payload("WARNING", "not-a-timestamp"));

        verify(sensorDeviceRepository, never()).findByMqttTopic(any());
        verify(environmentAlertRepository, never()).save(any());
    }

    /**
     * Verifies telemetry for unknown or deleted sensors is ignored.
     */
    @Test
    void ingestSkipsUnknownSensorTopic() {
        when(sensorDeviceRepository.findByMqttTopic(TOPIC)).thenReturn(Optional.empty());

        telemetryIngestionService.ingest(payload("CRITICAL", "2026-04-05T00:00:00Z"));

        verify(environmentAlertRepository, never()).save(any());
    }

    private SensimulPayload payload(final String status, final String timestamp) {
        return new SensimulPayload("site-a", "sensor-01", "temperature", "temperature", 12.5, "C", status,
                timestamp, 10L, "1.0");
    }

    private SensorDevice sensorDevice(final Long id, final String name, final String unit) {
        final SensorDevice sensorDevice = new SensorDevice();
        sensorDevice.setId(id);
        sensorDevice.setName(name);
        sensorDevice.setMqttTopic(TOPIC);
        sensorDevice.setUnit(unit);
        sensorDevice.setDeleted(false);
        sensorDevice.setActive(true);
        return sensorDevice;
    }

    private EnvironmentAlert activeAlert(final Long sensorDeviceId, final AlertSeverity severity) {
        final EnvironmentAlert alert = new EnvironmentAlert();
        alert.setId(99L);
        alert.setSensorDeviceId(sensorDeviceId);
        alert.setAlertType("temperature");
        alert.setSeverity(severity);
        alert.setMessage("existing");
        alert.setAcknowledged(false);
        return alert;
    }
}
