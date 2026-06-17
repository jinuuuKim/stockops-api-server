package com.stockops.notification.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link NotificationDeliveryLogger} — URL masking and field mapping.
 *
 * @author StockOps Team
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryLoggerTest {

    @Mock
    private NotificationDeliveryLogRepository repository;

    @Test
    void masksWebhookUrlAndNeverStoresTheSignedToken() {
        final NotificationDeliveryLogger logger = new NotificationDeliveryLogger(repository);
        final String signedUrl =
                "https://abc.environment.api.powerplatform.com:443/powerautomate/.../invoke?sig=TOP_SECRET_TOKEN";
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType("ENVIRONMENT_ALERT")
                .severity(WebhookPayload.Severity.CRITICAL)
                .alertType("TEMPERATURE_THRESHOLD")
                .eventTitle("[강서창고] 온도 임계치")
                .message("센서: -8.5°C (CRITICAL)")
                .guidanceSource("FALLBACK")
                .build();

        logger.record("TEAMS", signedUrl, payload, NotificationDeliveryLog.Status.SENT, null);

        final ArgumentCaptor<NotificationDeliveryLog> captor =
                ArgumentCaptor.forClass(NotificationDeliveryLog.class);
        verify(repository).save(captor.capture());
        final NotificationDeliveryLog saved = captor.getValue();

        assertThat(saved.getWebhookTarget()).contains("abc.environment.api.powerplatform.com").contains("#");
        assertThat(saved.getWebhookTarget()).doesNotContain("TOP_SECRET_TOKEN").doesNotContain("sig=");
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getEventType()).isEqualTo("ENVIRONMENT_ALERT");
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getTitle()).isEqualTo("[강서창고] 온도 임계치");
        assertThat(saved.getGuidanceSource()).isEqualTo("FALLBACK");
    }

    @Test
    void recordingNeverThrowsWhenRepositoryFails() {
        org.mockito.Mockito.when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("db down"));
        final NotificationDeliveryLogger logger = new NotificationDeliveryLogger(repository);

        // Must not propagate — delivery logging is best-effort.
        logger.record("TEAMS", "https://x.powerplatform.com/y", null,
                NotificationDeliveryLog.Status.FAILED, "boom");
    }
}
