package com.stockops.notification.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.stockops.notification.config.NotificationChannelConfig;
import com.stockops.notification.config.NotificationChannelConfig.ChannelEntry;
import com.stockops.notification.config.NotificationChannelConfig.ChannelType;
import com.stockops.notification.config.NotificationChannelConfigService;
import com.stockops.notification.email.EmailService;
import com.stockops.notification.escalation.EscalationRule;
import com.stockops.notification.escalation.PendingAlert;
import com.stockops.notification.sms.SmsService;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Orchestrates notification dispatch by reading channel configuration
 * and routing alerts to the appropriate services (SMS, Email, Webhook).
 *
 * <p>Resolution logic:
 * <ol>
 *   <li>Reads NotificationChannelConfig for the alert's center/warehouse/alertType</li>
 *   <li>For each channel in the escalation rule, checks if it's enabled in the config</li>
 *   <li>If enabled, dispatches via the appropriate service</li>
 *   <li>If no config found, falls back to in-app notification only</li>
 * </ol>
 *
 * @author StockOps Team
 * @since 2.0
 * @see NotificationChannelConfigService
 * @see SmsService
 * @see EmailService
 * @see WebhookService
 */
@Service
public class NotificationDispatcher {

    private final NotificationChannelConfigService channelConfigService;
    private final SmsService smsService;
    private final Optional<EmailService> emailService;
    private final WebhookService webhookService;
    private final WebhookEndpointConfigRepository webhookEndpointConfigRepository;

    /**
     * Dispatches a notification for a pending alert through configured channels.
     *
     * @param alert        the pending alert being dispatched
     * @param rule         the escalation rule defining channels and target roles
     * @param phoneNumbers resolved phone numbers for SMS recipients
     * @param emailAddresses resolved email addresses for Email recipients
     */
    public void dispatch(final PendingAlert alert, final EscalationRule rule,
                         final List<String> phoneNumbers, final List<String> emailAddresses) {
        Optional<NotificationChannelConfig> configOpt = channelConfigService.resolveChannels(
                alert.getCenterId(), alert.getWarehouseId(), alert.getAlertType());

        if (configOpt.isEmpty()) {
            log.info("[DISPATCH] No channel config found for center={}, warehouse={}, alertType={}. "
                    + "Using default channels from escalation rule.",
                    alert.getCenterId(), alert.getWarehouseId(), alert.getAlertType());
            dispatchWithRuleDefaults(alert, rule, phoneNumbers, emailAddresses);
            return;
        }

        NotificationChannelConfig config = configOpt.get();
        dispatchWithConfig(alert, rule, config, phoneNumbers, emailAddresses);
    }

    private void dispatchWithConfig(final PendingAlert alert, final EscalationRule rule,
                                    final NotificationChannelConfig config,
                                    final List<String> phoneNumbers,
                                    final List<String> emailAddresses) {
        for (String channel : rule.getChannels()) {
            ChannelType channelType;
            try {
                channelType = ChannelType.valueOf(channel.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[DISPATCH] Unknown channel type '{}', skipping", channel);
                continue;
            }

            boolean enabled = config.getChannels().stream()
                    .filter(ch -> ch.getType() == channelType && ch.isEnabled())
                    .findFirst()
                    .isPresent();

            if (!enabled) {
                log.info("[DISPATCH] Channel {} disabled in config for alertType={}, skipping",
                        channel, alert.getAlertType());
                continue;
            }

            switch (channelType) {
                case SMS -> dispatchSms(alert, rule, phoneNumbers);
                case EMAIL -> dispatchEmail(alert, rule, emailAddresses);
                case WEBHOOK -> dispatchWebhook(alert, rule, config);
            }
        }
    }

    private void dispatchWithRuleDefaults(final PendingAlert alert, final EscalationRule rule,
                                          final List<String> phoneNumbers,
                                          final List<String> emailAddresses) {
        for (String channel : rule.getChannels()) {
            switch (channel.toUpperCase()) {
                case "SMS" -> dispatchSms(alert, rule, phoneNumbers);
                case "EMAIL" -> dispatchEmail(alert, rule, emailAddresses);
                case "WEBHOOK" -> log.info("[DISPATCH] No channel config for WEBHOOK, skipping webhook dispatch");
                default -> log.warn("[DISPATCH] Unknown channel type '{}', skipping", channel);
            }
        }
    }

    private void dispatchSms(final PendingAlert alert, final EscalationRule rule,
                             final List<String> phoneNumbers) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            log.warn("[DISPATCH] No phone numbers available for SMS dispatch");
            return;
        }
        String message = formatMessage(alert, rule);
        for (String phoneNumber : phoneNumbers) {
            try {
                smsService.send(phoneNumber, message);
                log.info("[DISPATCH] SMS sent to {}", phoneNumber);
            } catch (Exception e) {
                log.error("[DISPATCH] Failed to send SMS to {}: {}", phoneNumber, e.getMessage(), e);
            }
        }
    }

    private void dispatchEmail(final PendingAlert alert, final EscalationRule rule,
                               final List<String> emailAddresses) {
        if (emailAddresses == null || emailAddresses.isEmpty()) {
            log.warn("[DISPATCH] No email addresses available for Email dispatch");
            return;
        }
        String subject = String.format("[ESCALATION L%d] %s Alert", rule.getLevel(), alert.getAlertType());
        String message = formatMessage(alert, rule);
        for (String email : emailAddresses) {
            try {
                if (emailService.isEmpty()) {
                    log.warn("[DISPATCH] EmailService is not available; skipping email dispatch to {}", email);
                    continue;
                }
                emailService.get().sendAlert(email, subject, message, alert.getSeverity());
                log.info("[DISPATCH] Email sent to {}", email);
            } catch (Exception e) {
                log.error("[DISPATCH] Failed to send email to {}: {}", email, e.getMessage(), e);
            }
        }
    }

    private void dispatchWebhook(final PendingAlert alert, final EscalationRule rule,
                                 final NotificationChannelConfig config) {
        ChannelEntry webhookEntry = config.getChannels().stream()
                .filter(ch -> ch.getType() == ChannelType.WEBHOOK && ch.isEnabled())
                .findFirst()
                .orElse(null);

        if (webhookEntry == null || webhookEntry.getWebhookProvider() == null) {
            log.warn("[DISPATCH] No webhook provider configured for alertType={}", alert.getAlertType());
            return;
        }

        String providerType = webhookEntry.getWebhookProvider();
        List<WebhookEndpointConfig> endpoints = webhookEndpointConfigRepository
                .findByCenterIdAndProviderTypeAndEnabledTrue(
                        config.getCenterId(),
                        WebhookEndpointConfig.WebhookProviderType.valueOf(providerType));

        if (endpoints.isEmpty()) {
            log.warn("[DISPATCH] No enabled webhook endpoints for provider={}", providerType);
            return;
        }

        WebhookPayload payload = WebhookPayload.builder()
                .eventType("ESCALATION")
                .message(formatMessage(alert, rule))
                .severity(mapSeverity(alert.getSeverity()))
                .alertType(alert.getAlertType())
                .centerName(String.valueOf(alert.getCenterId()))
                .warehouseName(alert.getWarehouseId() != null ? String.valueOf(alert.getWarehouseId()) : null)
                .timestamp(alert.getCreatedAt())
                .details(java.util.Map.of("level", String.valueOf(rule.getLevel())))
                .build();

        for (WebhookEndpointConfig endpoint : endpoints) {
            try {
                webhookService.send(providerType, endpoint.getWebhookUrl(), payload);
                log.info("[DISPATCH] Webhook sent via {} to {}", providerType, endpoint.getWebhookUrl());
            } catch (Exception e) {
                log.error("[DISPATCH] Failed to send webhook via {}: {}", providerType, e.getMessage(), e);
            }
        }
    }

    private String formatMessage(final PendingAlert alert, final EscalationRule rule) {
        return String.format("[ESCALATION L%d] %s alert at center=%d%s: %s",
                rule.getLevel(),
                alert.getSeverity(),
                alert.getCenterId(),
                alert.getWarehouseId() != null ? "/warehouse=" + alert.getWarehouseId() : "",
                alert.getMessage());
    }

    private WebhookPayload.Severity mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> WebhookPayload.Severity.CRITICAL;
            case "WARNING" -> WebhookPayload.Severity.WARNING;
            default -> WebhookPayload.Severity.INFO;
        };
    }

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    public NotificationDispatcher(final NotificationChannelConfigService channelConfigService, final SmsService smsService, final Optional<EmailService> emailService, final WebhookService webhookService, final WebhookEndpointConfigRepository webhookEndpointConfigRepository) {
        this.channelConfigService = channelConfigService;
        this.smsService = smsService;
        this.emailService = emailService;
        this.webhookService = webhookService;
        this.webhookEndpointConfigRepository = webhookEndpointConfigRepository;
    }
}
