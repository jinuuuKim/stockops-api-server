package com.stockops.notification.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** Closing watermark line appended to every card. */
    private static final String WATERMARK = "_Stockops에서 발송되었습니다._";

    // Package-private for unit testing of the rendered MessageCard shape.
    Map<String, Object> buildTeamsPayload(final WebhookPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("@type", "MessageCard");
        body.put("@context", "https://schema.org/extensions");
        body.put("themeColor", severityHex(payload.severity()));
        body.put("summary", firstNonBlank(payload.alertName(), payload.eventType()));
        body.put("title", formatTitle(payload));
        // Body text: Korean event title line, falling back to the raw message.
        final String eventLine = firstNonBlank(payload.eventTitle(), payload.message());
        if (eventLine != null) {
            body.put("text", "**" + eventLine + "**");
        }

        java.util.List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("activityTitle", firstNonBlank(payload.alertName(), payload.eventType()));

        java.util.List<Map<String, Object>> facts = new ArrayList<>();
        addFact(facts, "경고수준", severityLabelKo(payload.severity()));
        if (payload.permissionLabel() != null) {
            addFact(facts, "권한", payload.permissionLabel());
        }
        final String place = firstNonBlank(payload.location(), payload.warehouseName(), payload.centerName());
        if (place != null) {
            addFact(facts, "위치", place);
        }
        if (payload.configuredValue() != null) {
            addFact(facts, "설정값", payload.configuredValue());
        }
        if (payload.currentValue() != null) {
            addFact(facts, "현재 값", payload.currentValue());
        }
        if (payload.statusLabel() != null) {
            addFact(facts, "상태", payload.statusLabel());
        }
        if (payload.alertType() != null) {
            addFact(facts, "유형", payload.alertType());
        }
        if (payload.details() != null && !payload.details().isEmpty()) {
            payload.details().forEach((k, v) -> addFact(facts, k, v));
        }
        section.put("facts", facts);

        // Remediation guidance (AI / Knowledge Base) rendered as the section body.
        if (payload.guidance() != null && !payload.guidance().isBlank()) {
            section.put("text", "📋 **상세 조치안내**\n\n" + payload.guidance());
        }
        sections.add(section);

        // Watermark as its own trailing section so it reads as a footer.
        Map<String, Object> watermark = new LinkedHashMap<>();
        watermark.put("text", WATERMARK);
        sections.add(watermark);

        body.put("sections", sections);
        return body;
    }

    private String formatTitle(final WebhookPayload payload) {
        StringBuilder sb = new StringBuilder();
        if (payload.permissionLabel() != null) {
            sb.append("[").append(payload.permissionLabel()).append("] · ");
        }
        sb.append("[").append(severityLabelKo(payload.severity())).append("] ");
        sb.append(firstNonBlank(payload.alertName(), payload.eventType()));
        return sb.toString();
    }

    private String severityHex(final WebhookPayload.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "FF0000";
            case WARNING -> "FFA500";
            case INFO -> "36A64F";
        };
    }

    private String severityLabelKo(final WebhookPayload.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "위험";
            case WARNING -> "경고";
            case INFO -> "안내";
        };
    }

    private static String firstNonBlank(final String... values) {
        for (final String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
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
            // Pass a URI, not a String: a String URL is treated as a UriTemplate and re-encoded,
            // which corrupts already-encoded SAS query params (e.g. Power Automate's sp=%2F.../sig)
            // and yields 401 AuthorizationFailed. URI.create keeps the URL verbatim.
            restTemplate.postForEntity(java.net.URI.create(webhookUrl), entity, String.class);
            log.info("[TEAMS] Webhook sent successfully: eventType={}", body.get("title"));
        } catch (RestClientException e) {
            log.error("[TEAMS] Webhook send failed: {}", e.getMessage(), e);
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[TEAMS] Failed to serialize payload: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookProvider.class);
}
