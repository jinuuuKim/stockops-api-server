package com.stockops.notification.escalation;

import com.stockops.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing pending alerts: creation, acknowledgment, and querying.
 *
 * @author StockOps Team
 * @since 2.0
 * @see PendingAlert
 * @see EscalationScheduler
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PendingAlertService {

    private final PendingAlertRepository pendingAlertRepository;

    /**
     * Creates a new pending alert from an environment monitoring trigger.
     *
     * @param alertType    alert type (e.g. TEMPERATURE, HUMIDITY)
     * @param centerId     center identifier
     * @param warehouseId  warehouse identifier (nullable)
     * @param sensorId     sensor identifier (nullable)
     * @param message      human-readable alert message
     * @param severity     severity level (e.g. WARNING, CRITICAL)
     * @return created pending alert
     */
    public PendingAlert createAlert(final String alertType, final Long centerId,
                                     final Long warehouseId, final Long sensorId,
                                     final String message, final String severity) {
        final PendingAlert alert = new PendingAlert();
        alert.setAlertType(alertType);
        alert.setCenterId(centerId);
        alert.setWarehouseId(warehouseId);
        alert.setSensorId(sensorId);
        alert.setMessage(message);
        alert.setSeverity(severity);
        alert.setStatus(PendingAlertStatus.PENDING);
        alert.setCurrentLevel(0);
        return pendingAlertRepository.save(alert);
    }

    /**
     * Acknowledges a pending alert, stopping further escalation.
     *
     * @param alertId        alert identifier
     * @param acknowledgedBy username of the acknowledging user
     * @return acknowledged alert
     * @throws ResourceNotFoundException if alert not found
     * @throws IllegalStateException    if alert is not in PENDING status
     */
    public PendingAlert acknowledge(final Long alertId, final String acknowledgedBy) {
        final PendingAlert alert = pendingAlertRepository
                .findByIdAndStatus(alertId, PendingAlertStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pending alert not found or already processed: " + alertId));

        alert.setStatus(PendingAlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgedBy(acknowledgedBy);
        return pendingAlertRepository.save(alert);
    }

    /**
     * Returns all pending alerts with the given status.
     *
     * @param status filter status
     * @return matching alerts
     */
    @Transactional(readOnly = true)
    public List<PendingAlert> findByStatus(final PendingAlertStatus status) {
        return pendingAlertRepository.findByStatus(status);
    }

    /**
     * Returns a pending alert by ID.
     *
     * @param id alert identifier
     * @return the alert
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PendingAlert findById(final Long id) {
        return pendingAlertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pending alert not found: " + id));
    }
}