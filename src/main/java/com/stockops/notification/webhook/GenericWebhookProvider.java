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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic webhook provider that sends the payload as-is (plain JSON).
 * Allows custom headers for authentication or content negotiation.
 *
 * <p>Use this provider for services that accept simple JSON payloads
 * or for custom integrations that don't match a specific provider format.</p>
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
public class GenericWebhookProvider implements WebhookProvider {

    private static final String PROVIDER_TYPE = "GENERIC";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GenericWebhookProvider(final RestTemplateBuilder restTemplateBuilder,
                                  final ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
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

        Map<String, Object> body = buildGenericPayload(payload);
        postJson(webhookUrl, body, headers);
    }

    private Map<String, Object> buildGenericPayload(final WebhookPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", payload.eventType());
        body.put("message", payload.message());
        body.put("severity", payload.severity().name());
        body.put("timestamp", payload.timestamp().toString());

        if (payload.location() != null) {
            body.put("location", payload.location());
        }
        if (payload.alertType() != null) {
            body.put("alertType", payload.alertType());
        }
        if (payload.centerName() != null) {
            body.put("centerName", payload.centerName());
        }
        if (payload.warehouseName() != null) {
            body.put("warehouseName", payload.warehouseName());
        }
        if (payload.details() != null && !payload.details().isEmpty()) {
            body.put("details", payload.details());
        }

        return body;
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
            log.info("[GENERIC] Webhook sent successfully: eventType={}", body.get("eventType"));
        } catch (RestClientException e) {
            log.error("[GENERIC] Webhook send failed: {}", e.getMessage(), e);
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[GENERIC] Failed to serialize payload: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}