package com.stockops.notification.sms;

import com.stockops.config.SmsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Twilio REST API implementation of SmsGateway.
 * Sends SMS via Twilio's v1/Accounts/{AccountSid}/Messages endpoint.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.enabled", havingValue = "true")
public class TwilioSmsGateway implements SmsGateway {

    private static final String TWILIO_API_URL_TEMPLATE =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final RestTemplate restTemplate;
    private final SmsConfig smsConfig;

    public TwilioSmsGateway(final RestTemplateBuilder restTemplateBuilder, final SmsConfig smsConfig) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.smsConfig = smsConfig;
    }

    @Override
    public SmsResult send(final String phoneNumber, final String message) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        final String url = String.format(TWILIO_API_URL_TEMPLATE, smsConfig.getTwilio().getAccountSid());

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(
                smsConfig.getTwilio().getAccountSid(),
                smsConfig.getTwilio().getAuthToken()
        );

        final String body = "To=" + phoneNumber +
                "&From=" + smsConfig.getTwilio().getFromNumber() +
                "&Body=" + message;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TwilioResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TwilioResponse.class
            );

            TwilioResponse body2 = response.getBody();
            if (body2 != null && body2.messageSid != null) {
                log.info("SMS sent successfully to {} via Twilio, messageSid={}", phoneNumber, body2.messageSid);
                return SmsResult.ok(body2.messageSid);
            } else {
                log.error("Twilio response missing messageSid for phone={}", phoneNumber);
                return SmsResult.failure("Twilio response missing messageSid");
            }

        } catch (HttpClientErrorException e) {
            log.error("Twilio client error {} for phone={}: {}", e.getStatusCode(), phoneNumber, e.getResponseBodyAsString());
            return SmsResult.failure("Client error: " + e.getStatusCode());

        } catch (HttpServerErrorException e) {
            log.error("Twilio server error {} for phone={}: {}", e.getStatusCode(), phoneNumber, e.getResponseBodyAsString());
            return SmsResult.failure("Server error: " + e.getStatusCode());

        } catch (RestClientException e) {
            log.error("Twilio connection error for phone={}: {}", phoneNumber, e.getMessage());
            return SmsResult.failure("Connection error: " + e.getMessage());
        }
    }

    record TwilioResponse(String messageSid, String status, String errorMessage) {}
}
