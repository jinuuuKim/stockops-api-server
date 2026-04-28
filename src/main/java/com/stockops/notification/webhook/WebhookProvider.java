package com.stockops.notification.webhook;

import java.util.Map;

/**
 * Contract for webhook providers. Each implementation formats the payload
 * for a specific external service (Slack, Discord, Teams, Notion, etc.)
 * and sends it via HTTP POST.
 *
 * <p>Implementations must be safe for concurrent use.</p>
 *
 * @author StockOps Team
 * @since 1.0
 * @see SlackWebhookProvider
 * @see DiscordWebhookProvider
 * @see TeamsWebhookProvider
 * @see NotionWebhookProvider
 * @see GenericWebhookProvider
 */
public interface WebhookProvider {

    /**
     * Returns the unique provider type identifier.
     * Used by {@link WebhookProviderRegistry} to look up the correct provider.
     *
     * @return provider type string (e.g. "SLACK", "DISCORD", "TEAMS", "NOTION", "GENERIC")
     */
    String getProviderType();

    /**
     * Sends a webhook payload to the given URL using provider-specific formatting.
     *
     * @param webhookUrl target webhook endpoint URL
     * @param payload    standardised payload to format and send
     * @throws IllegalArgumentException if webhookUrl is blank or payload is null
     */
    void send(String webhookUrl, WebhookPayload payload);

    /**
     * Sends a webhook payload with additional custom HTTP headers.
     * Useful for providers that require authentication tokens or custom headers.
     *
     * @param webhookUrl target webhook endpoint URL
     * @param payload    standardised payload to format and send
     * @param headers    additional HTTP headers to include in the request
     * @throws IllegalArgumentException if webhookUrl is blank or payload is null
     */
    void send(String webhookUrl, WebhookPayload payload, Map<String, String> headers);
}