package com.stockops.notification.kakao;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * KakaoTalk configuration properties bound from application-pi.yml.
 * Supports future Kakao Business API integration (KakaoBizMsg or similar).
 *
 * @author StockOps Team
 * @since 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "kakao")
public class KakaoTalkConfig {

    /**
     * Whether KakaoTalk notifications are enabled.
     * When false, all KakaoTalk operations are stubbed (logged only).
     */
    private boolean enabled = false;

    /**
     * Kakao Business API key or access token.
     */
    private String apiKey;

    /**
     * Sender key registered in KakaoTalk Business Center.
     */
    private String senderKey;

    /**
     * Base URL for Kakao Business API endpoints.
     */
    private String baseUrl;
}