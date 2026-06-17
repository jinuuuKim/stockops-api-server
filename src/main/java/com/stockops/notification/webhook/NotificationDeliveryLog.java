package com.stockops.notification.webhook;

import com.stockops.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only record of every webhook notification delivery attempt — what message was sent where,
 * and whether it succeeded. The webhook URL is stored masked (host + hash) so the {@code sig} token
 * is never persisted in plaintext. {@code created_at} (from {@link BaseEntity}) is the send time.
 *
 * @author StockOps Team
 * @since 2.3
 */
@Entity
@Table(name = "notification_delivery_log")
public class NotificationDeliveryLog extends BaseEntity {

    /** Outcome of a delivery attempt. */
    public enum Status { SENT, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "alert_type", length = 100)
    private String alertType;

    @Column(name = "severity", length = 30)
    private String severity;

    @Column(name = "provider", length = 30)
    private String provider;

    /** Masked webhook target (host + hash); never the raw signed URL. */
    @Column(name = "webhook_target", length = 255)
    private String webhookTarget;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "guidance_source", length = 500)
    private String guidanceSource;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public NotificationDeliveryLog() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getEventType() {
        return this.eventType;
    }

    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    public String getAlertType() {
        return this.alertType;
    }

    public void setAlertType(final String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return this.severity;
    }

    public void setSeverity(final String severity) {
        this.severity = severity;
    }

    public String getProvider() {
        return this.provider;
    }

    public void setProvider(final String provider) {
        this.provider = provider;
    }

    public String getWebhookTarget() {
        return this.webhookTarget;
    }

    public void setWebhookTarget(final String webhookTarget) {
        this.webhookTarget = webhookTarget;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getGuidanceSource() {
        return this.guidanceSource;
    }

    public void setGuidanceSource(final String guidanceSource) {
        this.guidanceSource = guidanceSource;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
