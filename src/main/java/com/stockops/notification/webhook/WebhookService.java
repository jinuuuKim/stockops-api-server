package com.stockops.notification.webhook;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Orchestrates webhook dispatch by looking up the correct provider
 * from the registry and delegating the send operation.
 *
 * <p>If the webhook URL is blank/empty, the payload is logged without
 * making an HTTP call (mock mode). This allows development and testing
 * without configuring external endpoints.</p>
 *
 * @author StockOps Team
 * @since 1.0
 * @see WebhookProviderRegistry
 * @see WebhookProvider
 */
@Service
public class WebhookService {

    private final WebhookProviderRegistry registry;
    private final NotificationDeliveryLogger deliveryLogger;
    private final ObservationRegistry observationRegistry;

    /**
     * Sends a webhook notification using the specified provider type.
     * Falls back to logging if the URL is not configured or the provider is unknown.
     *
     * @param providerType the target provider type (e.g. "SLACK", "DISCORD")
     * @param webhookUrl   the webhook endpoint URL; if blank, payload is logged only
     * @param payload      the notification payload to send
     */
    public void send(final String providerType, final String webhookUrl, final WebhookPayload payload) {
        send(providerType, webhookUrl, payload, Map.of());
    }

    /**
     * Sends a webhook notification with custom HTTP headers.
     *
     * @param providerType the target provider type
     * @param webhookUrl   the webhook endpoint URL; if blank, payload is logged only
     * @param payload      the notification payload to send
     * @param headers      additional HTTP headers for the request
     */
    public void send(final String providerType, final String webhookUrl,
                     final WebhookPayload payload, final Map<String, String> headers) {
        // Manual span around the dispatch so the webhook send latency and outcome
        // (SENT / FAILED / SKIPPED) are traceable per provider and event type.
        final Observation observation = Observation.createNotStarted(
                        "notification.webhook.send", observationRegistry)
                .contextualName("webhook-send")
                .lowCardinalityKeyValue("provider", providerType == null ? "unknown" : providerType)
                .highCardinalityKeyValue("event_type",
                        payload == null || payload.eventType() == null ? "unknown" : payload.eventType());
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.info("[WEBHOOK MOCK] providerType={}, payload={}", providerType, payload);
                observation.lowCardinalityKeyValue("outcome", "SKIPPED");
                deliveryLogger.record(providerType, webhookUrl, payload,
                        NotificationDeliveryLog.Status.SKIPPED, "blank webhook URL (mock mode)");
                return;
            }

            var providerOpt = registry.getProvider(providerType);
            if (providerOpt.isEmpty()) {
                log.error("[WEBHOOK] Unknown provider type '{}'. Available: {}",
                        providerType, registry.getRegisteredTypes());
                observation.lowCardinalityKeyValue("outcome", "SKIPPED");
                deliveryLogger.record(providerType, webhookUrl, payload,
                        NotificationDeliveryLog.Status.SKIPPED, "unknown provider type");
                return;
            }

            WebhookProvider provider = providerOpt.get();
            try {
                provider.send(webhookUrl, payload, headers);
                log.info("[WEBHOOK] Sent via {}: eventType={}", providerType, payload.eventType());
                observation.lowCardinalityKeyValue("outcome", "SENT");
                deliveryLogger.record(providerType, webhookUrl, payload,
                        NotificationDeliveryLog.Status.SENT, null);
            } catch (Exception e) {
                log.error("[WEBHOOK] Failed to send via {}: eventType={}, error={}",
                        providerType, payload.eventType(), e.getMessage(), e);
                observation.lowCardinalityKeyValue("outcome", "FAILED");
                observation.error(e);
                deliveryLogger.record(providerType, webhookUrl, payload,
                        NotificationDeliveryLog.Status.FAILED, e.getMessage());
            }
        } finally {
            observation.stop();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public WebhookService(final WebhookProviderRegistry registry,
                          final NotificationDeliveryLogger deliveryLogger,
                          final ObservationRegistry observationRegistry) {
        this.registry = registry;
        this.deliveryLogger = deliveryLogger;
        this.observationRegistry = observationRegistry;
    }

}