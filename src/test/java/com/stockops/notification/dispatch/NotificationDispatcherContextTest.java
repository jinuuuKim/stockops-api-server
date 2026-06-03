package com.stockops.notification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.stockops.notification.config.NotificationChannelConfigService;
import com.stockops.notification.email.EmailService;
import com.stockops.notification.sms.SmsService;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationDispatcherContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(NotificationChannelConfigService.class, () -> mock(NotificationChannelConfigService.class))
            .withBean(SmsService.class, () -> mock(SmsService.class))
            .withBean(WebhookService.class, () -> mock(WebhookService.class))
            .withBean(WebhookEndpointConfigRepository.class, () -> mock(WebhookEndpointConfigRepository.class))
            .withBean(NotificationDispatcher.class);

    @Test
    void startsWithoutEmailServiceWhenMailIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationDispatcher.class);
            assertThat(context).doesNotHaveBean(EmailService.class);
        });
    }
}
