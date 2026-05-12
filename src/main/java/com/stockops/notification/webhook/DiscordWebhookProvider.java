package com.stockops.notification.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Discord webhook provider that formats payloads as Discord embeds.
 * Sends JSON with embed objects containing title, description, color, and fields.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
public class DiscordWebhookProvider implements WebhookProvider {

    private static final String PROVIDER_TYPE = "DISCORD";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DiscordWebhookProvider(final RestTemplateBuilder restTemplateBuilder,
                                  final ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderType() {
        return PROVIDER_TYPE;
    }

    @Override
    public void send(final String webhookUrl, final WebhookPayload payload) {
        send(webhookUrl, payload, Map.of());
    }

    @Override
    public void send(final String webhookUrl, final WebhookPayload payload,
                     final Map<String, String> headers) {
        validate(webhookUrl, payload);

        Map<String, Object> body = buildDiscordPayload(payload);
        postJson(webhookUrl, body, headers);
    }

    private Map<String, Object> buildDiscordPayload(final WebhookPayload payload) {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", payload.eventType());
        embed.put("description", payload.message());
        embed.put("color", severityColor(payload.severity()));
        embed.put("timestamp", payload.timestamp().toString());

        java.util.List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, "Severity", payload.severity().name(), true);
        if (payload.centerName() != null) {
            addField(fields, "Center", payload.centerName(), true);
        }
        if (payload.warehouseName() != null) {
            addField(fields, "Warehouse", payload.warehouseName(), true);
        }
        if (payload.location() != null) {
            addField(fields, "Location", payload.location(), true);
        }
        if (payload.alertType() != null) {
            addField(fields, "Alert Type", payload.alertType(), true);
        }
        if (payload.details() != null && !payload.details().isEmpty()) {
            payload.details().forEach((k, v) -> addField(fields, k, v, false));
        }

        embed.put("fields", fields);

        Map<String, Object> body = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> embeds = new ArrayList<>();
        embeds.add(embed);
        body.put("embeds", embeds);

        return body;
    }

    private int severityColor(final WebhookPayload.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0xFF0000;
            case WARNING -> 0xFFA500;
            case INFO -> 0x36A64F;
        };
    }

    private void addField(final java.util.List<Map<String, Object>> fields,
                          final String name, final String value, final boolean inline) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        fields.add(field);
    }

    private void validate(final String webhookUrl, final WebhookPayload payload) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }

    private void postJson(final String webhookUrl, final Map<String, Object> body,
                          final Map<String, String> extraHeaders) {
        try {
            String json = objectMapper.writeValueAsString(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            extraHeaders.forEach(headers::set);

            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("[DISCORD] Webhook sent successfully: eventType={}", body.get("embeds"));
        } catch (RestClientException e) {
            log.error("[DISCORD] Webhook send failed: {}", e.getMessage(), e);
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[DISCORD] Failed to serialize payload: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
