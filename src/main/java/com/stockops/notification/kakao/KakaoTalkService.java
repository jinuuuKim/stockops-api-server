package com.stockops.notification.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * KakaoTalk notification service (stub implementation).
 *
 * Currently logs all requests to console. Ready for real Kakao Business API integration
 * (e.g., KakaoBizMsg, KakaoTalk Messaging API) by replacing stub methods with HTTP calls.
 *
 * @author StockOps Team
 * @since 1.0
 * @see KakaoTalkConfig
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoTalkService {

    private final KakaoTalkConfig config;

    /**
     * Sends an alert templated message via KakaoTalk.
     *
     * @param phoneNumber  destination phone number in E.164 format
     * @param templateCode Kakao Business template code (e.g., "alert_001")
     * @param params       key-value pairs matching template variables
     * @return true (stub always returns true; real impl returns API result)
     */
    public boolean sendAlert(final String phoneNumber, final String templateCode,
                              final Map<String, String> params) {
        log.info("[KAKAO STUB] sendAlert to {} with template {} and params {}",
                phoneNumber, templateCode, params);
        return true;
    }

    /**
     * Sends a free-form friend talk message via KakaoTalk.
     *
     * @param phoneNumber destination phone number in E.164 format
     * @param message     text content to send
     * @return true (stub always returns true; real impl returns API result)
     */
    public boolean sendFriendTalk(final String phoneNumber, final String message) {
        log.info("[KAKAO STUB] sendFriendTalk to {} with message: {}",
                phoneNumber, message);
        return true;
    }
}