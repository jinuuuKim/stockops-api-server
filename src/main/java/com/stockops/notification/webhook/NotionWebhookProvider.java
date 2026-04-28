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
 * Notion webhook provider that formats payloads for Notion Integrations.
 * Sends a simplified JSON structure suitable for creating a page or database entry
 * via Notion's API.
 *
 * <p>Note: Full Notion API integration requires an integration token in the headers.
 * This provider formats the payload but the caller must supply the Authorization
 * and Notion-Version headers via the extra headers map.</p>
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
public class NotionWebhookProvider implements WebhookProvider {

    private static final String PROVIDER_TYPE = "NOTION";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NotionWebhookProvider(final RestTemplateBuilder restTemplateBuilder,
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

        Map<String, Object> body = buildNotionPayload(payload);
        postJson(webhookUrl, body, headers);
    }

    private Map<String, Object> buildNotionPayload(final WebhookPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();

        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("page_id", extractPageId(payload));
        body.put("parent", parent);

        java.util.List<Map<String, Object>> properties = new java.util.ArrayList<>();

        Map<String, Object> titleProp = new LinkedHashMap<>();
        titleProp.put("name", "Name");
        Map<String, Object> titleContent = new LinkedHashMap<>();
        titleContent.put("content", formatTitle(payload));
        titleProp.put("title", java.util.List.of(titleContent));
        properties.add(titleProp);

        Map<String, Object> severityProp = new LinkedHashMap<>();
        severityProp.put("name", "Severity");
        Map<String, Object> severityContent = new LinkedHashMap<>();
        severityContent.put("content", payload.severity().name());
        severityProp.put("rich_text", java.util.List.of(severityContent));
        properties.add(severityProp);

        Map<String, Object> messageProp = new LinkedHashMap<>();
        messageProp.put("name", "Message");
        Map<String, Object> messageContent = new LinkedHashMap<>();
        messageContent.put("content", payload.message());
        messageProp.put("rich_text", java.util.List.of(messageContent));
        properties.add(messageProp);

        if (payload.centerName() != null) {
            Map<String, Object> centerProp = new LinkedHashMap<>();
            centerProp.put("name", "Center");
            Map<String, Object> centerContent = new LinkedHashMap<>();
            centerContent.put("content", payload.centerName());
            centerProp.put("rich_text", java.util.List.of(centerContent));
            properties.add(centerProp);
        }

        if (payload.warehouseName() != null) {
            Map<String, Object> warehouseProp = new LinkedHashMap<>();
            warehouseProp.put("name", "Warehouse");
            Map<String, Object> warehouseContent = new LinkedHashMap<>();
            warehouseContent.put("content", payload.warehouseName());
            warehouseProp.put("rich_text", java.util.List.of(warehouseContent));
            properties.add(warehouseProp);
        }

        if (payload.location() != null) {
            Map<String, Object> locationProp = new LinkedHashMap<>();
            locationProp.put("name", "Location");
            Map<String, Object> locationContent = new LinkedHashMap<>();
            locationContent.put("content", payload.location());
            locationProp.put("rich_text", java.util.List.of(locationContent));
            properties.add(locationProp);
        }

        body.put("properties", properties);

        java.util.List<Map<String, Object>> children = new java.util.ArrayList<>();
        Map<String, Object> paragraph = new LinkedHashMap<>();
        paragraph.put("object", "block");
        paragraph.put("type", "paragraph");
        Map<String, Object> paragraphData = new LinkedHashMap<>();
        Map<String, Object> richText = new LinkedHashMap<>();
        richText.put("content", payload.message());
        paragraphData.put("rich_text", java.util.List.of(richText));
        paragraph.put("paragraph", paragraphData);
        children.add(paragraph);
        body.put("children", children);

        return body;
    }

    private String extractPageId(final WebhookPayload payload) {
        if (payload.details() != null && payload.details().containsKey("notionPageId")) {
            return payload.details().get("notionPageId");
        }
        return "default";
    }

    private String formatTitle(final WebhookPayload payload) {
        return "[" + payload.severity().name() + "] " + payload.eventType();
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
            log.info("[NOTION] Webhook sent successfully: eventType={}", body.get("parent"));
        } catch (RestClientException e) {
            log.error("[NOTION] Webhook send failed: {}", e.getMessage(), e);
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[NOTION] Failed to serialize payload: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}