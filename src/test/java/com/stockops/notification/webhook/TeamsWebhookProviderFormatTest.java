package com.stockops.notification.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

/**
 * Verifies the Korean MessageCard format produced by {@link TeamsWebhookProvider}.
 *
 * @author StockOps Team
 */
class TeamsWebhookProviderFormatTest {

    private final TeamsWebhookProvider provider =
            new TeamsWebhookProvider(new RestTemplateBuilder(), new ObjectMapper());

    @Test
    void rendersKoreanCriticalCardWithAllFields() {
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType("ENVIRONMENT_ALERT")
                .severity(WebhookPayload.Severity.CRITICAL)
                .alertType("TEMPERATURE_THRESHOLD")
                .permissionLabel("창고관리자")
                .alertName("센서 임계치 알림")
                .eventTitle("[서울 강서 냉동/냉장 창고] 인천 송도 냉동 온도 센서 · 센서 임계치 알림")
                .location("서울 강서 냉동/냉장 창고")
                .configuredValue("허용 -30.0 ~ -12.0°C")
                .currentValue("-8.5°C")
                .statusLabel("초과")
                .guidance("1) 냉동기 가동 상태 확인 2) 도어 밀폐 점검 3) 창고관리자 즉시 호출")
                .build();

        final Map<String, Object> card = provider.buildTeamsPayload(payload);

        assertThat(card.get("themeColor")).isEqualTo("FF0000");
        assertThat((String) card.get("title")).contains("[창고관리자]").contains("[위험]").contains("센서 임계치 알림");
        assertThat((String) card.get("text")).contains("서울 강서 냉동/냉장 창고");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> sections = (List<Map<String, Object>>) card.get("sections");
        // first section = facts + guidance, last section = watermark
        assertThat(sections).hasSize(2);

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> facts = (List<Map<String, Object>>) sections.get(0).get("facts");
        final Map<String, String> factMap = facts.stream()
                .collect(java.util.stream.Collectors.toMap(
                        f -> (String) f.get("name"), f -> (String) f.get("value")));
        assertThat(factMap).containsEntry("경고수준", "위험");
        assertThat(factMap).containsEntry("권한", "창고관리자");
        assertThat(factMap).containsEntry("설정값", "허용 -30.0 ~ -12.0°C");
        assertThat(factMap).containsEntry("현재 값", "-8.5°C");
        assertThat(factMap).containsEntry("상태", "초과");
        assertThat(factMap).containsKey("유형");

        assertThat((String) sections.get(0).get("text")).contains("상세 조치안내").contains("냉동기");
        assertThat((String) sections.get(1).get("text")).contains("Stockops에서 발송");
    }

    @Test
    void degradesGracefullyWhenKoreanFieldsMissing() {
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType("NOTICE")
                .severity(WebhookPayload.Severity.INFO)
                .message("점검 안내입니다")
                .build();

        final Map<String, Object> card = provider.buildTeamsPayload(payload);

        assertThat(card.get("themeColor")).isEqualTo("36A64F");
        assertThat((String) card.get("title")).contains("[안내]").contains("NOTICE");
        assertThat((String) card.get("text")).contains("점검 안내입니다");
    }
}
