package com.stockops.environment;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.SensorDevice;
import com.stockops.notification.email.EmailService;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends best-effort notifications (webhook + email) when an environment alert is opened or escalated.
 *
 * <p>Notification is fired on the state-change event (when a sensor enters or worsens a
 * warning/critical state), not on every reading, and never on auto-resolution. All dispatch is
 * wrapped so a notification failure can never break telemetry ingestion or roll back the alert.
 *
 * @author StockOps Team
 * @since 2.2
 */
@Service
public class EnvironmentAlertNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentAlertNotifier.class);
    private static final String EVENT_TYPE = "ENVIRONMENT_ALERT";

    private final WebhookService webhookService;
    private final WebhookEndpointConfigRepository webhookEndpointConfigRepository;
    private final Optional<EmailService> emailService;
    private final List<String> emailRecipients;

    /**
     * Creates the notifier.
     *
     * @param webhookService webhook dispatch service
     * @param webhookEndpointConfigRepository enabled webhook endpoint lookup
     * @param emailService optional email service (absent when mail is not configured)
     * @param emailRecipients comma-separated admin recipients for environment alert emails
     */
    public EnvironmentAlertNotifier(
            final WebhookService webhookService,
            final WebhookEndpointConfigRepository webhookEndpointConfigRepository,
            final Optional<EmailService> emailService,
            @Value("${stockops.environment.alert-email-recipients:}") final String emailRecipients) {
        this.webhookService = webhookService;
        this.webhookEndpointConfigRepository = webhookEndpointConfigRepository;
        this.emailService = emailService;
        this.emailRecipients = parseRecipients(emailRecipients);
    }

    /**
     * Notifies configured channels that an environment alert opened or escalated.
     * Best-effort: any failure is logged and swallowed.
     *
     * @param alert the opened/escalated alert
     * @param device the related sensor device (may be null)
     */
    public void notifyAlertOpened(final EnvironmentAlert alert, final SensorDevice device) {
        try {
            dispatchWebhooks(alert, device);
            dispatchEmails(alert, device);
        } catch (final RuntimeException exception) {
            LOGGER.warn("Environment alert notification failed (non-fatal) for alertId={}: {}",
                    alert.getId(), exception.getMessage());
        }
    }

    private void dispatchWebhooks(final EnvironmentAlert alert, final SensorDevice device) {
        final List<WebhookEndpointConfig> endpoints = webhookEndpointConfigRepository.findByEnabledTrue();
        if (endpoints.isEmpty()) {
            return;
        }
        final WebhookPayload payload = WebhookPayload.builder()
                .eventType(EVENT_TYPE)
                .message(alert.getMessage())
                .severity(toWebhookSeverity(alert.getSeverity()))
                .location(device == null ? null : device.getLocation())
                .alertType(alert.getAlertType())
                .timestamp(Instant.now())
                .build();
        for (final WebhookEndpointConfig endpoint : endpoints) {
            webhookService.send(endpoint.getProviderType().name(), endpoint.getWebhookUrl(), payload);
        }
    }

    private void dispatchEmails(final EnvironmentAlert alert, final SensorDevice device) {
        if (emailService.isEmpty() || emailRecipients.isEmpty()) {
            return;
        }
        final String sensorName = device == null ? "센서" : device.getName();
        final String subject = String.format("[%s] 환경 센서 알림 - %s", alert.getSeverity(), sensorName);
        for (final String recipient : emailRecipients) {
            emailService.get().sendAlert(recipient, subject, alert.getMessage(), alert.getSeverity().name());
        }
    }

    private WebhookPayload.Severity toWebhookSeverity(final AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> WebhookPayload.Severity.CRITICAL;
            case WARNING -> WebhookPayload.Severity.WARNING;
            default -> WebhookPayload.Severity.INFO;
        };
    }

    private List<String> parseRecipients(final String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
