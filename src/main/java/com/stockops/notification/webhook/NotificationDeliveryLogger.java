package com.stockops.notification.webhook;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists a {@link NotificationDeliveryLog} row for each webhook delivery attempt. Logging never
 * throws into the caller: a failure here must not break (or roll back) notification delivery.
 *
 * @author StockOps Team
 * @since 2.3
 */
@Component
public class NotificationDeliveryLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryLogger.class);
    private static final int MESSAGE_MAX = 4000;

    private final NotificationDeliveryLogRepository repository;

    public NotificationDeliveryLogger(final NotificationDeliveryLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records one delivery attempt.
     *
     * @param providerType provider type (e.g. "TEAMS")
     * @param webhookUrl   raw webhook URL (stored masked); may be null for SKIPPED
     * @param payload      the notification payload
     * @param status       delivery outcome
     * @param errorMessage failure detail, or null
     */
    public void record(final String providerType, final String webhookUrl, final WebhookPayload payload,
                       final NotificationDeliveryLog.Status status, final String errorMessage) {
        try {
            final NotificationDeliveryLog row = new NotificationDeliveryLog();
            row.setProvider(providerType);
            row.setWebhookTarget(maskUrl(webhookUrl));
            row.setStatus(status.name());
            row.setErrorMessage(truncate(errorMessage, 1000));
            if (payload != null) {
                row.setEventType(payload.eventType());
                row.setAlertType(payload.alertType());
                row.setSeverity(payload.severity() == null ? null : payload.severity().name());
                row.setTitle(truncate(firstNonBlank(payload.eventTitle(), payload.alertName(),
                        payload.eventType()), 255));
                row.setMessage(truncate(payload.message(), MESSAGE_MAX));
                row.setGuidanceSource(truncate(payload.guidanceSource(), 500));
            }
            repository.save(row);
        } catch (final RuntimeException e) {
            LOGGER.warn("Failed to persist notification delivery log (status={}): {}", status, e.getMessage());
        }
    }

    /** Masks a webhook URL to {@code host#<sha256-prefix>} so the signed token is never stored. */
    private String maskUrl(final String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (final IllegalArgumentException e) {
            host = null;
        }
        return (host == null ? "unknown" : host) + "#" + shortHash(url);
    }

    private String shortHash(final String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (final Exception e) {
            return "nohash";
        }
    }

    private static String firstNonBlank(final String... values) {
        for (final String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(final String value, final int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
