package com.stockops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only environment alert entity.
 * Captures sensor-driven and system-generated operational alerts with acknowledgement metadata.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Entity
@Table(name = "environment_alerts")
public class EnvironmentAlert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_device_id")
    private Long sensorDeviceId;

    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private AlertSeverity severity = AlertSeverity.INFO;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "acknowledgement_note", length = 1000)
    private String acknowledgementNote;

    /** Measured value that triggered the alert ("현재 값"); null for non-numeric events (e.g. door). */
    @Column(name = "reading_value")
    private Double readingValue;

    /** Unit for {@link #readingValue}, e.g. "°C". */
    @Column(name = "reading_unit", length = 30)
    private String readingUnit;

    public EnvironmentAlert() {
    }

    public Double getReadingValue() {
        return this.readingValue;
    }

    public void setReadingValue(final Double readingValue) {
        this.readingValue = readingValue;
    }

    public String getReadingUnit() {
        return this.readingUnit;
    }

    public void setReadingUnit(final String readingUnit) {
        this.readingUnit = readingUnit;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Long getSensorDeviceId() {
        return this.sensorDeviceId;
    }

    public void setSensorDeviceId(final Long sensorDeviceId) {
        this.sensorDeviceId = sensorDeviceId;
    }

    public String getAlertType() {
        return this.alertType;
    }

    public void setAlertType(final String alertType) {
        this.alertType = alertType;
    }

    public AlertSeverity getSeverity() {
        return this.severity;
    }

    public void setSeverity(final AlertSeverity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public boolean isAcknowledged() {
        return this.acknowledged;
    }

    public void setAcknowledged(final boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public Instant getAcknowledgedAt() {
        return this.acknowledgedAt;
    }

    public void setAcknowledgedAt(final Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return this.resolvedAt;
    }

    public void setResolvedAt(final Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getAcknowledgementNote() {
        return this.acknowledgementNote;
    }

    public void setAcknowledgementNote(final String acknowledgementNote) {
        this.acknowledgementNote = acknowledgementNote;
    }

    public String getAcknowledgedBy() {
        return this.acknowledgedBy;
    }

    public void setAcknowledgedBy(final String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }
}
