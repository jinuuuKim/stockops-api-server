package com.stockops.ai.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Spring Boot client for the Python AI forecast service (Prophet).
 * <p>
 * Provides synchronous REST calls with configurable timeouts and a simple
 * counter-based circuit breaker. When the circuit is open or the call fails,
 * methods return {@code null} so callers can fall back to a local model.
 *
 * <p>Endpoints consumed:
 * <ul>
 *   <li>{@code POST /predict} – single-product demand forecast</li>
 *   <li>{@code POST /predict/bulk} – bulk demand forecast</li>
 * </ul>
 *
 * @author StockOps Team
 * @since 2.0
 * @see ProphetForecastModel
 */
@Component
public class AiForecastClient {

    private final RestTemplate restTemplate;
    private final AiForecastProperties properties;

    private final Object circuitLock = new Object();
    private CircuitState circuitState = CircuitState.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openCircuitUntil;

    /**
     * Constructs the client with timeout-configured {@link RestTemplate}.
     *
     * @param properties AI service configuration (URL, timeouts, circuit-breaker)
     */
    public AiForecastClient(final AiForecastProperties properties) {
        this.properties = properties;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Fetches a demand forecast for a single product from the Prophet service.
     *
     * @param productId product identifier
     * @param days      number of days to forecast
     * @return forecast response, or {@code null} when the circuit is open or the call fails
     */
    public AiForecastResponse getForecast(final Long productId, final int days) {
        if (isCircuitOpen()) {
            log.warn("AI forecast circuit breaker is OPEN; skipping request for productId={}", productId);
            return null;
        }

        final String url = properties.getUrl() + "/predict";
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.set("X-API-Key", properties.getApiKey());
        }
        final HttpEntity<AiForecastRequest> request = new HttpEntity<>(new AiForecastRequest(productId, days), headers);

        try {
            final AiForecastResponse response = restTemplate.postForObject(url, request, AiForecastResponse.class);
            recordSuccess();
            return response;
        } catch (final RestClientResponseException e) {
            log.error("AI forecast HTTP error for productId={}: status={}, body={}",
                    productId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            recordFailure();
            return null;
        } catch (final ResourceAccessException e) {
            log.error("AI forecast timeout/connection error for productId={}: {}", productId, e.getMessage());
            recordFailure();
            return null;
        } catch (final Exception e) {
            log.error("AI forecast unexpected error for productId={}", productId, e);
            recordFailure();
            return null;
        }
    }

    /**
     * Fetches bulk demand forecasts for multiple products from the Prophet service.
     *
     * @param productIds list of product identifiers
     * @param days       number of days to forecast
     * @return list of forecast responses, or {@code null} when the circuit is open or the call fails
     */
    public List<AiForecastResponse> getBulkForecasts(final List<Long> productIds, final int days) {
        if (isCircuitOpen()) {
            log.warn("AI forecast circuit breaker is OPEN; skipping bulk request for {} products", productIds.size());
            return null;
        }

        final String url = properties.getUrl() + "/predict/bulk";
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.set("X-API-Key", properties.getApiKey());
        }
        final HttpEntity<AiBulkForecastRequest> request = new HttpEntity<>(new AiBulkForecastRequest(productIds, days), headers);

        try {
            final ResponseEntity<List<AiForecastResponse>> responseEntity = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
            recordSuccess();
            return responseEntity.getBody();
        } catch (final RestClientResponseException e) {
            log.error("AI bulk forecast HTTP error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            recordFailure();
            return null;
        } catch (final ResourceAccessException e) {
            log.error("AI bulk forecast timeout/connection error: {}", e.getMessage());
            recordFailure();
            return null;
        } catch (final Exception e) {
            log.error("AI bulk forecast unexpected error", e);
            recordFailure();
            return null;
        }
    }

    private boolean isCircuitOpen() {
        synchronized (circuitLock) {
            if (circuitState == CircuitState.OPEN) {
                if (Instant.now().isAfter(openCircuitUntil)) {
                    circuitState = CircuitState.HALF_OPEN;
                    log.info("AI forecast circuit breaker moved from OPEN to HALF_OPEN");
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    private void recordSuccess() {
        synchronized (circuitLock) {
            consecutiveFailures = 0;
            if (circuitState == CircuitState.HALF_OPEN) {
                circuitState = CircuitState.CLOSED;
                log.info("AI forecast circuit breaker moved from HALF_OPEN to CLOSED");
            }
        }
    }

    private void recordFailure() {
        synchronized (circuitLock) {
            consecutiveFailures++;
            if (circuitState == CircuitState.HALF_OPEN) {
                circuitState = CircuitState.OPEN;
                openCircuitUntil = Instant.now().plus(properties.getCircuitBreakerCooldown());
                log.warn("AI forecast circuit breaker moved from HALF_OPEN to OPEN until {}", openCircuitUntil);
            } else if (consecutiveFailures >= properties.getCircuitBreakerFailureThreshold()) {
                circuitState = CircuitState.OPEN;
                openCircuitUntil = Instant.now().plus(properties.getCircuitBreakerCooldown());
                log.warn("AI forecast circuit breaker moved from CLOSED to OPEN until {} ({} consecutive failures)",
                        openCircuitUntil, consecutiveFailures);
            }
        }
    }

    private enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    /**
     * Request payload for {@code POST /predict}.
     *
     * @param productId product identifier
     * @param days      number of days to forecast
     */
    public record AiForecastRequest(Long productId, int days) {
    }

    /**
     * Response payload from {@code POST /predict}.
     *
     * @param productId product identifier
     * @param days      number of forecasted days
     * @param forecast  daily forecast points
     */
    public record AiForecastResponse(Long productId, int days, List<ForecastPoint> forecast) {

        /**
         * Single daily forecast point from Prophet.
         *
         * @param ds   date string (ISO-8601)
         * @param yhat predicted demand
         */
        public record ForecastPoint(String ds, double yhat) {
        }
    }

    /**
     * Request payload for {@code POST /predict/bulk}.
     *
     * @param productIds list of product identifiers
     * @param days       number of days to forecast
     */
    public record AiBulkForecastRequest(List<Long> productIds, int days) {
    }

    private static final Logger log = LoggerFactory.getLogger(AiForecastClient.class);
}
