package com.stockops.notification.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookProviderRegistry registry;

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
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[WEBHOOK MOCK] providerType={}, payload={}", providerType, payload);
            return;
        }

        var providerOpt = registry.getProvider(providerType);
        if (providerOpt.isEmpty()) {
            log.error("[WEBHOOK] Unknown provider type '{}'. Available: {}",
                    providerType, registry.getRegisteredTypes());
            return;
        }

        WebhookProvider provider = providerOpt.get();
        try {
            provider.send(webhookUrl, payload, headers);
            log.info("[WEBHOOK] Sent via {}: eventType={}", providerType, payload.eventType());
        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to send via {}: eventType={}, error={}",
                    providerType, payload.eventType(), e.getMessage(), e);
        }
    }
}