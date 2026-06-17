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
        Map<String, String> details,
        /**
         * Korean responsible-role label shown in the card header ("권한"), e.g. "창고관리자".
         */
        String permissionLabel,
        /**
         * Korean alert name ("알림명"), e.g. "센서 임계치 알림".
         */
        String alertName,
        /**
         * Korean event title line ("이벤트명"), e.g. "[서울 강서 냉동/냉장 창고] 온도센서 임계치 초과".
         */
        String eventTitle,
        /**
         * Configured / threshold value ("설정값"), pre-formatted with unit if any.
         */
        String configuredValue,
        /**
         * Current measured value ("현재 값"), pre-formatted with unit if any.
         */
        String currentValue,
        /**
         * Status label ("상태"), e.g. "초과", "미달", "정상".
         */
        String statusLabel,
        /**
         * AI/Knowledge-Base sourced remediation guidance ("상세 설명").
         */
        String guidance,
        /**
         * Provenance of the guidance for delivery auditing (e.g. "KNOWLEDGE_BASE", "FALLBACK",
         * or a KB citation). Not rendered on the card; recorded in the delivery log.
         */
        String guidanceSource
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
        private String permissionLabel;
        private String alertName;
        private String eventTitle;
        private String configuredValue;
        private String currentValue;
        private String statusLabel;
        private String guidance;
        private String guidanceSource;

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

        public Builder permissionLabel(String permissionLabel) {
            this.permissionLabel = permissionLabel;
            return this;
        }

        public Builder alertName(String alertName) {
            this.alertName = alertName;
            return this;
        }

        public Builder eventTitle(String eventTitle) {
            this.eventTitle = eventTitle;
            return this;
        }

        public Builder configuredValue(String configuredValue) {
            this.configuredValue = configuredValue;
            return this;
        }

        public Builder currentValue(String currentValue) {
            this.currentValue = currentValue;
            return this;
        }

        public Builder statusLabel(String statusLabel) {
            this.statusLabel = statusLabel;
            return this;
        }

        public Builder guidance(String guidance) {
            this.guidance = guidance;
            return this;
        }

        public Builder guidanceSource(String guidanceSource) {
            this.guidanceSource = guidanceSource;
            return this;
        }

        public WebhookPayload build() {
            return new WebhookPayload(
                    eventType, message, severity, location, timestamp,
                    alertType, centerName, warehouseName, details,
                    permissionLabel, alertName, eventTitle,
                    configuredValue, currentValue, statusLabel, guidance, guidanceSource
            );
        }
    }
}