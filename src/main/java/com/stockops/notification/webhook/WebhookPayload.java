package com.stockops.notification.webhook;

import java.time.Instant;
import java.util.Map;

/**
 * Standardised payload for all webhook notifications.
 * Each provider formats this into its own JSON structure before sending.
 *
 * @author StockOps Team
 * @since 1.0
 */
public record WebhookPayload(
        /**
         * Event category (e.g. "EXPIRY_ALERT", "ENVIRONMENT_ALERT", "INVENTORY_LOW").
         */
        String eventType,
        /**
         * Human-readable notification message.
         */
        String message,
        /**
         * Alert severity level.
         */
        Severity severity,
        /**
         * Location name where the event occurred.
         */
        String location,
        /**
         * Timestamp of the event.
         */
        Instant timestamp,
        /**
         * Alert type classification.
         */
        String alertType,
        /**
         * Center name associated with the event.
         */
        String centerName,
        /**
         * Warehouse name associated with the event.
         */
        String warehouseName,
        /**
         * Additional key-value details specific to the event.
         */
        Map<String, String> details
) {
    /**
     * Severity levels matching environment alert severities.
     */
    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /**
     * Builder for constructing WebhookPayload instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link WebhookPayload}.
     */
    public static class Builder {
        private String eventType;
        private String message;
        private Severity severity = Severity.INFO;
        private String location;
        private Instant timestamp = Instant.now();
        private String alertType;
        private String centerName;
        private String warehouseName;
        private Map<String, String> details = Map.of();

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder alertType(String alertType) {
            this.alertType = alertType;
            return this;
        }

        public Builder centerName(String centerName) {
            this.centerName = centerName;
            return this;
        }

        public Builder warehouseName(String warehouseName) {
            this.warehouseName = warehouseName;
            return this;
        }

        public Builder details(Map<String, String> details) {
            this.details = details;
            return this;
        }

        public WebhookPayload build() {
            return new WebhookPayload(
                    eventType, message, severity, location, timestamp,
                    alertType, centerName, warehouseName, details
            );
        }
    }
}