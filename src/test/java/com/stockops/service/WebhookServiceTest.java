package com.stockops.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.notification.webhook.NotificationDeliveryLogger;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookProvider;
import com.stockops.notification.webhook.WebhookProviderRegistry;
import com.stockops.notification.webhook.WebhookService;
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
    private WebhookProvider provider;

    @Mock
    private NotificationDeliveryLogger deliveryLogger;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(registry, deliveryLogger, ObservationRegistry.NOOP);
    }

    @Test
    void sendWithBlankUrlLogsOnly() {
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType("TEST")
                .message("test")
                .build();

        webhookService.send("SLACK", "", payload);

        verify(registry, never()).getProvider(any());
    }

    @Test
    void sendWithKnownProviderDelegates() {
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType("TEST")
                .message("test")
                .build();

        when(registry.getProvider("SLACK")).thenReturn(Optional.of(provider));

        webhookService.send("SLACK", "https://hooks.slack.com/test", payload);

        verify(provider).send("https://hooks.slack.com/test", payload, Map.of());
    }

    @Test
    void sendWithUnknownProviderLogsError() {
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType("TEST")
                .message("test")
                .build();

        when(registry.getProvider("UNKNOWN")).thenReturn(Optional.empty());
        when(registry.getRegisteredTypes()).thenReturn(java.util.Set.of("SLACK", "DISCORD"));

        webhookService.send("UNKNOWN", "https://example.com", payload);

        verify(provider, never()).send(any(), any(), any());
    }
}
