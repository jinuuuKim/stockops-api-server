package com.stockops.environment.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.SensorReading;
import com.stockops.repository.SensorDeviceRepository;
import com.stockops.repository.SensorReadingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link TelemetryIngestionService}.
 *
 * @author StockOps Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private SensorLatestProjectionRepository sensorLatestProjectionRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TelemetryIngestionService telemetryIngestionService;

    /**
     * Verifies that a valid payload stores history and creates the latest projection.
     */
    @Test
    void ingestPersistsReadingAndCreatesLatestProjection() throws Exception {
        final SensimulPayload payload = payload("site-a", "sensor-01", "", "2026-04-05T00:00:00Z", 10L);
        final SensorDevice sensorDevice = sensorDevice(5L, "sensimul/sites/site-a/sensors/sensor-01", "C");
        when(sensorDeviceRepository.findByMqttTopic(sensorDevice.getMqttTopic()))
                .thenReturn(Optional.of(sensorDevice));
        when(objectMapper.writeValueAsString(payload)).thenReturn("{json}");
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.empty());
        when(sensorLatestProjectionRepository.updateIfNewer(eq(5L), eq(12.5), eq("temperature"), eq("C"), eq("ok"),
                eq(java.time.Instant.parse("2026-04-05T00:00:00Z")), eq(10L), any(java.time.Instant.class)))
                .thenReturn(0);

        telemetryIngestionService.ingest(payload);

        final ArgumentCaptor<SensorReading> readingCaptor = ArgumentCaptor.forClass(SensorReading.class);
        verify(sensorReadingRepository).save(readingCaptor.capture());
        assertThat(readingCaptor.getValue().getSensorDeviceId()).isEqualTo(5L);
        assertThat(readingCaptor.getValue().getUnit()).isEqualTo("C");
        assertThat(readingCaptor.getValue().getRawPayload()).isEqualTo("{json}");

        final ArgumentCaptor<SensorLatestProjection> projectionCaptor = ArgumentCaptor.forClass(SensorLatestProjection.class);
        verify(sensorLatestProjectionRepository).save(projectionCaptor.capture());
        assertThat(projectionCaptor.getValue().getSensorDeviceId()).isEqualTo(5L);
        assertThat(projectionCaptor.getValue().getSequenceId()).isEqualTo(10L);
    }

    /**
     * Verifies that malformed payloads are ignored without any persistence.
     */
    @Test
    void ingestSkipsMalformedPayload() {
        telemetryIngestionService.ingest(new SensimulPayload(
                "",
                "sensor-01",
                "temperature",
                "temperature",
                12.5,
                "C",
                "ok",
                "2026-04-05T00:00:00Z",
                10L,
                ""));

        verify(sensorDeviceRepository, never()).findByMqttTopic(any());
        verify(sensorReadingRepository, never()).save(any());
    }

    /**
     * Verifies that invalid timestamps are dropped before repository access.
     */
    @Test
    void ingestSkipsInvalidTimestamp() {
        telemetryIngestionService.ingest(payload("site-a", "sensor-01", "C", "not-a-timestamp", 10L));

        verify(sensorDeviceRepository, never()).findByMqttTopic(any());
        verify(sensorReadingRepository, never()).save(any());
    }

    /**
     * Verifies that telemetry for unknown or deleted sensors is ignored.
     */
    @Test
    void ingestSkipsUnknownSensorTopic() {
        final SensimulPayload payload = payload("site-a", "sensor-01", "C", "2026-04-05T00:00:00Z", 10L);

        telemetryIngestionService.ingest(payload);

        verify(sensorReadingRepository, never()).save(any());
        verify(sensorLatestProjectionRepository, never()).save(any());
    }

    /**
     * Verifies that stale sequences are stored in history without mutating the latest projection.
     */
    @Test
    void ingestStoresStaleSequenceWithoutUpdatingProjection() throws Exception {
        final SensimulPayload payload = payload("site-a", "sensor-01", "C", "2026-04-05T00:00:00Z", 8L);
        final SensorDevice sensorDevice = sensorDevice(5L, "sensimul/sites/site-a/sensors/sensor-01", "C");
        final SensorLatestProjection latest = new SensorLatestProjection();
        latest.setSensorDeviceId(5L);
        latest.setSequenceId(10L);

        when(sensorDeviceRepository.findByMqttTopic(sensorDevice.getMqttTopic()))
                .thenReturn(Optional.of(sensorDevice));
        when(objectMapper.writeValueAsString(payload)).thenReturn("{json}");
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.of(latest));

        telemetryIngestionService.ingest(payload);

        verify(sensorReadingRepository).save(any(SensorReading.class));
        verify(sensorLatestProjectionRepository, never()).updateIfNewer(eq(5L), any(), any(), any(), any(), any(), any(), any());
        verify(sensorLatestProjectionRepository, never()).save(any());
    }

    /**
     * Verifies that a concurrent insert collision falls back to an update retry.
     */
    @Test
    void ingestRetriesProjectionUpdateAfterConcurrentInsertConflict() throws Exception {
        final SensimulPayload payload = payload("site-a", "sensor-01", "C", "2026-04-05T00:00:00Z", 10L);
        final SensorDevice sensorDevice = sensorDevice(5L, "sensimul/sites/site-a/sensors/sensor-01", "C");
        when(sensorDeviceRepository.findByMqttTopic(sensorDevice.getMqttTopic()))
                .thenReturn(Optional.of(sensorDevice));
        when(objectMapper.writeValueAsString(payload)).thenReturn("{json}");
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.empty());
        when(sensorLatestProjectionRepository.updateIfNewer(eq(5L), eq(12.5), eq("temperature"), eq("C"), eq("ok"),
                eq(java.time.Instant.parse("2026-04-05T00:00:00Z")), eq(10L), any(java.time.Instant.class)))
                .thenReturn(0, 1);
        when(sensorLatestProjectionRepository.save(any(SensorLatestProjection.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        telemetryIngestionService.ingest(payload);

        verify(sensorLatestProjectionRepository, times(2)).updateIfNewer(eq(5L), eq(12.5), eq("temperature"), eq("C"),
                eq("ok"), eq(java.time.Instant.parse("2026-04-05T00:00:00Z")), eq(10L), any(java.time.Instant.class));
    }

    /**
     * Verifies that payload serialization failures surface as illegal state exceptions.
     */
    @Test
    void ingestPropagatesSerializationFailure() throws Exception {
        final SensimulPayload payload = payload("site-a", "sensor-01", "C", "2026-04-05T00:00:00Z", 10L);
        final SensorDevice sensorDevice = sensorDevice(5L, "sensimul/sites/site-a/sensors/sensor-01", "C");
        when(sensorDeviceRepository.findByMqttTopic(sensorDevice.getMqttTopic()))
                .thenReturn(Optional.of(sensorDevice));
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("boom") { });

        assertThrows(IllegalStateException.class, () -> telemetryIngestionService.ingest(payload));
    }

    /**
     * Verifies that latest-reading lookup prefers the projection sequence when present.
     */
    @Test
    void getLatestReadingUsesProjectionSequenceWhenAvailable() {
        final SensorLatestProjection projection = new SensorLatestProjection();
        projection.setSensorDeviceId(5L);
        projection.setSequenceId(10L);
        final SensorReading reading = new SensorReading();
        reading.setSensorDeviceId(5L);
        reading.setSequenceId(10L);
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.of(projection));
        when(sensorReadingRepository.findTopBySensorDeviceIdAndSequenceIdOrderByRecordedAtDesc(5L, 10L))
                .thenReturn(Optional.of(reading));

        final Optional<SensorReading> latestReading = telemetryIngestionService.getLatestReading(5L);

        assertThat(latestReading).contains(reading);
    }

    /**
     * Verifies that latest-reading lookup falls back when no projection exists.
     */
    @Test
    void getLatestReadingFallsBackToHistoryWhenProjectionMissing() {
        final SensorReading reading = new SensorReading();
        reading.setSensorDeviceId(5L);
        reading.setSequenceId(11L);
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.empty());
        when(sensorReadingRepository.findTopBySensorDeviceIdOrderBySequenceIdDescRecordedAtDesc(5L))
                .thenReturn(Optional.of(reading));

        final Optional<SensorReading> latestReading = telemetryIngestionService.getLatestReading(5L);

        assertThat(latestReading).contains(reading);
    }

    /**
     * Verifies that a projection without sequence id falls back to the newest reading by timestamp.
     */
    @Test
    void getLatestReadingFallsBackToRecordedAtWhenProjectionHasNoSequence() {
        final SensorLatestProjection projection = new SensorLatestProjection();
        projection.setSensorDeviceId(5L);
        projection.setSequenceId(null);
        final SensorReading reading = new SensorReading();
        reading.setSensorDeviceId(5L);
        reading.setSequenceId(11L);
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.of(projection));
        when(sensorReadingRepository.findTopBySensorDeviceIdOrderByRecordedAtDesc(5L)).thenReturn(Optional.of(reading));

        final Optional<SensorReading> latestReading = telemetryIngestionService.getLatestReading(5L);

        assertThat(latestReading).contains(reading);
    }

    /**
     * Verifies that latest-reading lookup returns empty when neither projection nor history exists.
     */
    @Test
    void getLatestReadingReturnsEmptyWhenNoProjectionOrHistoryExists() {
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.empty());
        when(sensorReadingRepository.findTopBySensorDeviceIdOrderBySequenceIdDescRecordedAtDesc(5L))
                .thenReturn(Optional.empty());

        assertThat(telemetryIngestionService.getLatestReading(5L)).isEmpty();
    }

    /**
     * Verifies the stale-sequence predicate boundary behavior.
     */
    @Test
    void isSequenceStaleReturnsTrueOnlyWhenIncomingSequenceIsOlder() {
        final SensorLatestProjection projection = new SensorLatestProjection();
        projection.setSensorDeviceId(5L);
        projection.setSequenceId(10L);
        when(sensorLatestProjectionRepository.findBySensorDeviceId(5L)).thenReturn(Optional.of(projection));

        assertThat(telemetryIngestionService.isSequenceStale(5L, 9L)).isTrue();
        assertThat(telemetryIngestionService.isSequenceStale(5L, 10L)).isFalse();
    }

    private SensimulPayload payload(
            final String siteId,
            final String sensorId,
            final String unit,
            final String timestamp,
            final long sequenceId) {
        return new SensimulPayload(siteId, sensorId, "temperature", "temperature", 12.5, unit, "ok", timestamp,
                sequenceId, "1.0");
    }

    private SensorDevice sensorDevice(final Long id, final String topic, final String unit) {
        final SensorDevice sensorDevice = new SensorDevice();
        sensorDevice.setId(id);
        sensorDevice.setMqttTopic(topic);
        sensorDevice.setUnit(unit);
        sensorDevice.setDeleted(false);
        sensorDevice.setActive(true);
        return sensorDevice;
    }
}
