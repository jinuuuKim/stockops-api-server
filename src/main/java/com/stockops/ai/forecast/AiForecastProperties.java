package com.stockops.ai.forecast;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the external Python AI forecast service.
 *
 * @author StockOps Team
 * @since 2.0
 * @see AiForecastClient
 */
@ConfigurationProperties(prefix = "stockops.ai-service")
public class AiForecastProperties {

    private String url = "http://localhost:8000";

    private String apiKey = "";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(5);

    private int circuitBreakerFailureThreshold = 3;

    private Duration circuitBreakerCooldown = Duration.ofSeconds(30);

    public String getUrl() {
        return this.url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectTimeout() {
        return this.connectTimeout;
    }

    public void setConnectTimeout(final Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return this.readTimeout;
    }

    public void setReadTimeout(final Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getCircuitBreakerFailureThreshold() {
        return this.circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerFailureThreshold(final int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public Duration getCircuitBreakerCooldown() {
        return this.circuitBreakerCooldown;
    }

    public void setCircuitBreakerCooldown(final Duration circuitBreakerCooldown) {
        this.circuitBreakerCooldown = circuitBreakerCooldown;
    }
}
