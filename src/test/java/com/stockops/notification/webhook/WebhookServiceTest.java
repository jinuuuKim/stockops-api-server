package com.stockops.notification.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookProviderRegistry registry;

    @Mock
    private NotificationDeliveryLogger deliveryLogger;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(registry, deliveryLogger, ObservationRegistry.NOOP);
    }

    @Test
    void sendLogsWhenUrlIsBlank() {
        final WebhookPayload payload = testPayload();

        // Should not throw, just log
        webhookService.send("SLACK", "", payload);

        // No provider interaction expected
    }

    @Test
    void sendDelegatesToProvider() {
        final WebhookPayload payload = testPayload();
        final TestWebhookProvider provider = new TestWebhookProvider();

        when(registry.getProvider("SLACK")).thenReturn(Optional.of(provider));

        webhookService.send("SLACK", "https://hooks.slack.com/test", payload);

        // Provider should have been called
        assertThat(provider.wasCalled()).isTrue();
    }

    @Test
    void sendWithHeadersDelegatesToProvider() {
        final WebhookPayload payload = testPayload();
        final TestWebhookProvider provider = new TestWebhookProvider();
        final Map<String, String> headers = Map.of("Authorization", "Bearer token");

        when(registry.getProvider("DISCORD")).thenReturn(Optional.of(provider));

        webhookService.send("DISCORD", "https://discord.com/webhook/test", payload, headers);

        assertThat(provider.wasCalled()).isTrue();
    }

    @Test
    void sendLogsWhenProviderNotFound() {
        final WebhookPayload payload = testPayload();

        when(registry.getProvider("UNKNOWN")).thenReturn(Optional.empty());

        // Should not throw, just log error
        webhookService.send("UNKNOWN", "https://example.com/webhook", payload);
    }

    private WebhookPayload testPayload() {
        return WebhookPayload.builder()
                .eventType("Test")
                .message("Test content")
                .severity(WebhookPayload.Severity.INFO)
                .build();
    }

    private static class TestWebhookProvider implements WebhookProvider {
        private boolean called = false;

        @Override
        public String getProviderType() {
            return "TEST";
        }

        @Override
        public void send(String url, WebhookPayload payload) {
            this.called = true;
        }

        @Override
        public void send(String url, WebhookPayload payload, Map<String, String> headers) {
            this.called = true;
        }

        public boolean wasCalled() {
            return called;
        }
    }
}
