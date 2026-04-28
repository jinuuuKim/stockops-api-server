package com.stockops.notification.email;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Email notification service using Spring Mail and Thymeleaf templates.
 * Supports weekly reports, alerts, and password reset emails.
 *
 * <p>When {@code email.enabled=false}, emails are logged instead of sent (mock mode).
 *
 * @author StockOps Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private static final String FROM_EMAIL = "${EMAIL_FROM:noreply@stockops.local}";
    private static final String DEFAULT_FROM = "StockOps <noreply@stockops.local>";

    /**
     * Data class for weekly report email content.
     */
    public record WeeklyReportData(
            String centerName,
            String periodStart,
            String periodEnd,
            int totalInbound,
            int totalOutbound,
            double inboundChangePercent,
            double outboundChangePercent,
            java.util.List<ProductStats> topProducts
    ) {}

    /**
     * Data class for product statistics in weekly report.
     */
    public record ProductStats(String productName, int totalQuantity, String unit) {}

    /**
     * Sends a weekly report email with Thymeleaf HTML template.
     *
     * @param to      recipient email address
     * @param data    weekly report data
     * @throws RuntimeException if email sending fails
     */
    public void sendWeeklyReport(final String to, final WeeklyReportData data) {
        log.info("Preparing weekly report email for: {}", to);

        final Context context = new Context();
        context.setVariable("centerName", data.centerName());
        context.setVariable("periodStart", data.periodStart());
        context.setVariable("periodEnd", data.periodEnd());
        context.setVariable("totalInbound", data.totalInbound());
        context.setVariable("totalOutbound", data.totalOutbound());
        context.setVariable("inboundChangePercent", data.inboundChangePercent());
        context.setVariable("outboundChangePercent", data.outboundChangePercent());
        context.setVariable("topProducts", data.topProducts());
        context.setVariable("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        final String htmlContent = templateEngine.process("email/weekly-report", context);
        sendHtmlEmail(to, "StockOps 주간 보고서 - " + data.centerName(), htmlContent);
    }

    /**
     * Sends an alert email with severity level.
     *
     * @param to       recipient email address
     * @param subject  alert subject
     * @param message  alert message body
     * @param severity alert severity (INFO, WARNING, CRITICAL)
     */
    public void sendAlert(final String to, final String subject, final String message, final String severity) {
        log.info("Preparing alert email for: {} [{}]", to, severity);

        final Context context = new Context();
        context.setVariable("severity", severity);
        context.setVariable("alertSubject", subject);
        context.setVariable("alertMessage", message);
        context.setVariable("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        context.setVariable("dashboardUrl", "${APP_DASHBOARD_URL:#}");

        final String htmlContent = templateEngine.process("email/alert", context);
        sendHtmlEmail(to, "[" + severity + "] StockOps 알림 - " + subject, htmlContent);
    }

    /**
     * Sends a password reset email with reset token and URL.
     *
     * @param to         recipient email address
     * @param resetToken unique reset token
     * @param resetUrl   password reset URL with token
     */
    public void sendPasswordReset(final String to, final String resetToken, final String resetUrl) {
        log.info("Preparing password reset email for: {}", to);

        final Context context = new Context();
        context.setVariable("resetToken", resetToken);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expirationHours", 24);
        context.setVariable("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        final String htmlContent = templateEngine.process("email/password-reset", context);
        sendHtmlEmail(to, "StockOps 비밀번호 재설정 요청", htmlContent);
    }

    /**
     * Internal method to send HTML email or log in mock mode.
     *
     * @param to      recipient
     * @param subject email subject
     * @param html    HTML content
     */
    private void sendHtmlEmail(final String to, final String subject, final String html) {
        final boolean emailEnabled = Boolean.parseBoolean(
                System.getProperty("email.enabled",
                        System.getenv().getOrDefault("EMAIL_ENABLED", "false")));

        if (!emailEnabled) {
            log.info("=== MOCK EMAIL MODE (email.enabled=false) ===");
            log.info("To: {}", to);
            log.info("Subject: {}", subject);
            log.info("Content (truncated): {}", html.substring(0, Math.min(500, html.length())));
            log.info("=== END MOCK EMAIL ===");
            return;
        }

        try {
            final var mimeMessage = mailSender.createMimeMessage();
            final var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(DEFAULT_FROM);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", to);
        } catch (final Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        }
    }
}