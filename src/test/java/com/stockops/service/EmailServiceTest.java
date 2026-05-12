package com.stockops.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.stockops.notification.email.EmailService;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendWeeklyReportInMockMode() {
        when(templateEngine.process(anyString(), org.mockito.ArgumentMatchers.any(Context.class)))
                .thenReturn("<html>report</html>");

        final EmailService.WeeklyReportData data = new EmailService.WeeklyReportData(
                "Test Center", "2026-04-21", "2026-04-27",
                100, 50, 10.0, -5.0,
                List.of(new EmailService.ProductStats("Product A", 20, "EA"))
        );

        emailService.sendWeeklyReport("test@example.com", data);
    }

    @Test
    void sendAlertInMockMode() {
        when(templateEngine.process(anyString(), org.mockito.ArgumentMatchers.any(Context.class)))
                .thenReturn("<html>alert</html>");

        emailService.sendAlert("test@example.com", "Temperature Alert", "High temp", "CRITICAL");
    }
}
