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
 * Microsoft Teams webhook provider using legacy MessageCard format.
 * Sends JSON with title, text, and color-coded sections based on severity.
 *
 * <p>Note: Teams supports both MessageCard (legacy) and Adaptive Card (v2) formats.
 * This implementation uses MessageCard for broader compatibility.</p>
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
public class TeamsWebhookProvider implements WebhookProvider {

    private static final String PROVIDER_TYPE = "TEAMS";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TeamsWebhookProvider(final RestTemplateBuilder restTemplateBuilder,
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

        Map<String, Object> body = buildTeamsPayload(payload);
        postJson(webhookUrl, body, headers);
    }

    private Map<String, Object> buildTeamsPayload(final WebhookPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("@type", "MessageCard");
        body.put("@context", "https://schema.org/extensions");
        body.put("themeColor", severityHex(payload.severity()));
        body.put("summary", payload.eventType());
        body.put("title", formatTitle(payload));
        body.put("text", payload.message());

        java.util.List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("activityTitle", payload.eventType());

        java.util.List<Map<String, Object>> facts = new ArrayList<>();
        addFact(facts, "Severity", payload.severity().name());
        if (payload.centerName() != null) {
            addFact(facts, "Center", payload.centerName());
        }
        if (payload.warehouseName() != null) {
            addFact(facts, "Warehouse", payload.warehouseName());
        }
        if (payload.location() != null) {
            addFact(facts, "Location", payload.location());
        }
        if (payload.alertType() != null) {
            addFact(facts, "Alert Type", payload.alertType());
        }
        if (payload.details() != null && !payload.details().isEmpty()) {
            payload.details().forEach((k, v) -> addFact(facts, k, v));
        }

        section.put("facts", facts);
        sections.add(section);
        body.put("sections", sections);

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

    private String severityHex(final WebhookPayload.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "FF0000";
            case WARNING -> "FFA500";
            case INFO -> "36A64F";
        };
    }

    private void addFact(final java.util.List<Map<String, Object>> facts,
                        final String name, final String value) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("name", name);
        fact.put("value", value);
        facts.add(fact);
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
            log.info("[TEAMS] Webhook sent successfully: eventType={}", body.get("title"));
        } catch (RestClientException e) {
            log.error("[TEAMS] Webhook send failed: {}", e.getMessage(), e);
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[TEAMS] Failed to serialize payload: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
