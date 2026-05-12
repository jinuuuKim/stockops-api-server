package com.stockops.notification.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock
    private SmsGateway smsGateway;

    @Mock
    private SmsSendHistoryRepository historyRepository;

    @InjectMocks
    private SmsService smsService;

    @Test
    void sendReturnsSuccessOnFirstAttempt() {
        final SmsResult successResult = new SmsResult(true, "msg-001", null);
        when(smsGateway.send("+821012345678", "Test message")).thenReturn(successResult);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final SmsResult result = smsService.send("+821012345678", "Test message");

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg-001");
    }

    @Test
    void sendRetriesOnFailure() {
        final SmsResult failureResult = new SmsResult(false, null, "Gateway error");
        final SmsResult successResult = new SmsResult(true, "msg-002", null);

        when(smsGateway.send(anyString(), anyString()))
                .thenReturn(failureResult)
                .thenReturn(successResult);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final SmsResult result = smsService.send("+821012345678", "Test message");

        assertThat(result.success()).isTrue();
    }

    @Test
    void sendReturnsFailureAfterMaxRetries() {
        final SmsResult failureResult = new SmsResult(false, null, "Gateway error");
        when(smsGateway.send(anyString(), anyString())).thenReturn(failureResult);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final SmsResult result = smsService.send("+821012345678", "Test message");

        assertThat(result.success()).isFalse();
    }
}
