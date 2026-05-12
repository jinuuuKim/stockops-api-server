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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slack webhook provider that formats payloads as Slack Block Kit messages.
 * Sends a JSON body with text and color-coded attachments based on severity.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
public class SlackWebhookProvider implements WebhookProvider {

    private static final String PROVIDER_TYPE = "SLACK";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SlackWebhookProvider(final RestTemplateBuilder restTemplateBuilder,
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

        Map<String, Object> body = buildSlackPayload(payload);
        postJson(webhookUrl, body, headers);
    }

    private Map<String, Object> buildSlackPayload(final WebhookPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", formatTitle(payload));

        List<Map<String, Object>> attachments = new ArrayList<>();
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("color", severityColor(payload.severity()));
        attachment.put("title", payload.eventType());
        attachment.put("text", payload.message());
        attachment.put("ts", payload.timestamp().getEpochSecond());

        List<Map<String, Object>> fields = new ArrayList<>();
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

        attachment.put("fields", fields);
        attachments.add(attachment);
        body.put("attachments", attachments);

        return body;
    }

    private String formatTitle(final WebhookPayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(payload.severity().name()).append("] ");
        sb.append(payload.eventType());
        if (payload.centerName() != null) {
            sb.append(" - ").append(payload.centerName());
        }
        return sb.toString();
    }

    private String severityColor(final WebhookPayload.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#FF0000";
            case WARNING -> "#FFA500";
            case INFO -> "#36A64F";
        };
    }

    private void addField(final List<Map<String, Object>> fields,
                          final String title, final String value, final boolean shortField) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("title", title);
        field.put("value", value);
        field.put("short", shortField);
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
            log.info("[SLACK] Webhook sent successfully: eventType={}", body.get("text"));
        } catch (RestClientException e) {
            log.error("[SLACK] Webhook send failed: {}", e.getMessage(), e);
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[SLACK] Failed to serialize payload: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
