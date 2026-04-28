package com.stockops.notification.escalation;

import com.stockops.notification.sms.SmsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dispatches escalation notifications through configured channels.
 * Currently supports SMS via SmsService; EMAIL and SLACK are logged as placeholders.
 *
 * @author StockOps Team
 * @since 2.0
 * @see SmsService
 * @see EscalationScheduler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationDispatcher {

    private static final String CHANNEL_SMS = "SMS";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_SLACK = "SLACK";

    private final SmsService smsService;

    /**
     * Dispatches an escalation notification through all channels configured in the rule.
     *
     * @param alert   the pending alert being escalated
     * @param rule    the escalation rule defining channels and target roles
     * @param phoneNumbers resolved phone numbers for SMS recipients
     */
    public void dispatch(final PendingAlert alert, final EscalationRule rule, final List<String> phoneNumbers) {
        final String message = formatMessage(alert, rule);

        for (final String channel : rule.getChannels()) {
            switch (channel.toUpperCase()) {
                case CHANNEL_SMS -> dispatchSms(phoneNumbers, message);
                case CHANNEL_EMAIL -> dispatchEmail(alert, rule, message);
                case CHANNEL_SLACK -> dispatchSlack(alert, rule, message);
                default -> log.warn("Unknown escalation channel: {}, skipping", channel);
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

    private void dispatchSms(final List<String> phoneNumbers, final String message) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            log.warn("No phone numbers available for SMS dispatch");
            return;
        }
        for (final String phoneNumber : phoneNumbers) {
            try {
                smsService.send(phoneNumber, message);
                log.info("SMS dispatched to {}", phoneNumber);
            } catch (final Exception e) {
                log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage(), e);
            }
        }
    }

    private void dispatchEmail(final PendingAlert alert, final EscalationRule rule, final String message) {
        log.info("[EMAIL PLACEHOLDER] Would send email for alert id={}, level={}, roles={}",
                alert.getId(), rule.getLevel(), rule.getNotifyRoles());
    }

    private void dispatchSlack(final PendingAlert alert, final EscalationRule rule, final String message) {
        log.info("[SLACK PLACEHOLDER] Would send Slack notification for alert id={}, level={}, roles={}",
                alert.getId(), rule.getLevel(), rule.getNotifyRoles());
    }
}