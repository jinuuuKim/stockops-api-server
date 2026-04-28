package com.stockops.notification.escalation;

import com.stockops.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Persistent record of an environment alert awaiting escalation.
 * Stored in the database so that Pi restarts do not lose pending alerts.
 *
 * <p>Lifecycle: PENDING → (escalation levels) → ESCALATED | ACKNOWLEDGED</p>
 *
 * @author StockOps Team
 * @since 2.0
 * @see EscalationScheduler
 * @see PendingAlertStatus
 */
@Data
@Entity
@Table(name = "pending_alerts", indexes = {
        @Index(name = "idx_pending_alerts_status", columnList = "status"),
        @Index(name = "idx_pending_alerts_status_created", columnList = "status, created_at")
})
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PendingAlert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Alert type (e.g. TEMPERATURE, HUMIDITY, AIR_QUALITY). */
    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    /** Center this alert belongs to. */
    @Column(name = "center_id", nullable = false)
    private Long centerId;

    /** Warehouse this alert belongs to (nullable for center-level alerts). */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    /** Sensor identifier that triggered the alert. */
    @Column(name = "sensor_id")
    private Long sensorId;

    /** Human-readable alert message. */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Severity level (e.g. WARNING, CRITICAL). */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** Current escalation status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingAlertStatus status = PendingAlertStatus.PENDING;

    /** Current escalation level (0 = initial, increments with each escalation step). */
    @Column(name = "current_level", nullable = false)
    private Integer currentLevel = 0;

    /** Timestamp when the alert was acknowledged. */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** Username of the user who acknowledged the alert. */
    @Column(name = "acknowledged_by", length = 255)
    private String acknowledgedBy;
}