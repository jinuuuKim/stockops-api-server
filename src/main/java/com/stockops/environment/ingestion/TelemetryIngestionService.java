package com.stockops.environment.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.SensorReading;
import com.stockops.repository.SensorDeviceRepository;
import com.stockops.repository.SensorReadingRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Processes live Sensimul telemetry into history and latest-state projections.
 *
 * @author StockOps Team
 * @since 1.0
 * @see SensorReadingRepository
 * @see SensorLatestProjectionRepository
 */
@Service
public class TelemetryIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final SensorDeviceRepository sensorDeviceRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final SensorLatestProjectionRepository sensorLatestProjectionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates the telemetry ingestion service.
     *
     * @param sensorDeviceRepository sensor device repository
     * @param sensorReadingRepository sensor reading repository
     * @param sensorLatestProjectionRepository latest projection repository
     * @param objectMapper jackson object mapper
     */
    public TelemetryIngestionService(
            final SensorDeviceRepository sensorDeviceRepository,
            final SensorReadingRepository sensorReadingRepository,
            final SensorLatestProjectionRepository sensorLatestProjectionRepository,
            final ObjectMapper objectMapper) {
        this.sensorDeviceRepository = sensorDeviceRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.sensorLatestProjectionRepository = sensorLatestProjectionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingests a single telemetry payload.
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
        final Optional<SensorDevice> sensorDevice = sensorDeviceRepository.findByMqttTopicAndDeletedFalse(mqttTopic);
        if (sensorDevice.isEmpty()) {
            LOGGER.warn("Skipping telemetry for unknown or deleted sensor topic: {}", mqttTopic);
            return;
        }

        final SensorDevice device = sensorDevice.get();
        final SensorReading reading = new SensorReading();
        reading.setSensorDeviceId(device.getId());
        reading.setValue(payload.value());
        reading.setValueKind(payload.valueKind());
        reading.setUnit(resolveUnit(payload.unit(), device.getUnit()));
        reading.setStatus(payload.status());
        reading.setRecordedAt(recordedAt);
        reading.setSequenceId(payload.sequenceId());
        reading.setRawPayload(serializePayload(payload));
        sensorReadingRepository.save(reading);

        if (isSequenceStale(device.getId(), payload.sequenceId())) {
            LOGGER.debug(
                    "Stored out-of-order reading without updating latest projection for sensorDeviceId={}, sequenceId={}",
                    device.getId(),
                    payload.sequenceId());
            return;
        }

        upsertLatestProjection(device.getId(), reading);
    }

    /**
     * Returns the latest stored reading for a sensor device.
     *
     * @param sensorDeviceId sensor device id
     * @return latest reading when available
     */
    @Transactional(readOnly = true)
    public Optional<SensorReading> getLatestReading(final Long sensorDeviceId) {
        return sensorLatestProjectionRepository.findBySensorDeviceId(sensorDeviceId)
                .flatMap(projection -> {
                    if (projection.getSequenceId() != null) {
                        return sensorReadingRepository.findTopBySensorDeviceIdAndSequenceIdOrderByRecordedAtDesc(
                                sensorDeviceId, projection.getSequenceId());
                    }
                    return sensorReadingRepository.findTopBySensorDeviceIdOrderByRecordedAtDesc(sensorDeviceId);
                })
                .or(() -> sensorReadingRepository.findTopBySensorDeviceIdOrderBySequenceIdDescRecordedAtDesc(sensorDeviceId));
    }

    /**
     * Checks whether a sequence id is older than the current latest projection.
     *
     * @param sensorDeviceId sensor device id
     * @param sequenceId incoming sequence id
     * @return true when the sequence is stale
     */
    @Transactional(readOnly = true)
    public boolean isSequenceStale(final Long sensorDeviceId, final long sequenceId) {
        return sensorLatestProjectionRepository.findBySensorDeviceId(sensorDeviceId)
                .map(SensorLatestProjection::getSequenceId)
                .filter(currentSequenceId -> currentSequenceId != null && sequenceId < currentSequenceId)
                .isPresent();
    }

    private void upsertLatestProjection(final Long sensorDeviceId, final SensorReading reading) {
        final Instant updatedAt = Instant.now();
        final int updatedRows = sensorLatestProjectionRepository.updateIfNewer(
                sensorDeviceId,
                reading.getValue(),
                reading.getValueKind(),
                reading.getUnit(),
                reading.getStatus(),
                reading.getRecordedAt(),
                reading.getSequenceId(),
                updatedAt);
        if (updatedRows > 0) {
            return;
        }

        if (sensorLatestProjectionRepository.findBySensorDeviceId(sensorDeviceId).isPresent()) {
            return;
        }

        final SensorLatestProjection projection = new SensorLatestProjection();
        projection.setSensorDeviceId(sensorDeviceId);
        projection.setValue(reading.getValue());
        projection.setValueKind(reading.getValueKind());
        projection.setUnit(reading.getUnit());
        projection.setStatus(reading.getStatus());
        projection.setRecordedAt(reading.getRecordedAt());
        projection.setSequenceId(reading.getSequenceId());
        projection.setUpdatedAt(updatedAt);

        try {
            sensorLatestProjectionRepository.save(projection);
        } catch (DataIntegrityViolationException exception) {
            sensorLatestProjectionRepository.updateIfNewer(
                    sensorDeviceId,
                    reading.getValue(),
                    reading.getValueKind(),
                    reading.getUnit(),
                    reading.getStatus(),
                    reading.getRecordedAt(),
                    reading.getSequenceId(),
                    updatedAt);
        }
    }

    private boolean isPayloadValid(final SensimulPayload payload) {
        return payload != null
                && StringUtils.hasText(payload.siteId())
                && StringUtils.hasText(payload.sensorId())
                && payload.value() != null
                && StringUtils.hasText(payload.status())
                && StringUtils.hasText(payload.timestamp());
    }

    private Instant parseRecordedAt(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String serializePayload(final SensimulPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize raw telemetry payload", exception);
        }
    }

    private String resolveUnit(final String payloadUnit, final String defaultUnit) {
        return StringUtils.hasText(payloadUnit) ? payloadUnit : defaultUnit;
    }
}
