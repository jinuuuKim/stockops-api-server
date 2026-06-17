package com.stockops.ai.forecast;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
    private final ObservationRegistry observationRegistry;

    private final Object circuitLock = new Object();
    private CircuitState circuitState = CircuitState.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openCircuitUntil;

    /**
     * Constructs the client with a timeout-configured {@link RestTemplate}.
     * <p>
     * The {@link RestTemplate} is built through the auto-configured
     * {@link RestTemplateBuilder} so that Spring Boot's observation/tracing
     * customizers are applied. This is what propagates the W3C {@code traceparent}
     * header to the Python AI service, letting api-server and ai-module spans
     * join the same distributed trace.
     *
     * @param properties AI service configuration (URL, timeouts, circuit-breaker)
     * @param restTemplateBuilder Spring-managed builder carrying observation instrumentation
     */
    public AiForecastClient(final AiForecastProperties properties,
                            final RestTemplateBuilder restTemplateBuilder,
                            final ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.observationRegistry = observationRegistry;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .build();
    }

    /**
     * Fetches a demand forecast for a single product from the Prophet service.
     *
     * @param productId product identifier
     * @param days      number of days to forecast
     * @return forecast response, or {@code null} when the circuit is open or the call fails
     */
    @Observed(name = "ai.forecast.client", contextualName = "ai-forecast-predict")
    public AiForecastResponse getForecast(final Long productId, final int days) {
        tagCurrentObservation("product.id", productId);
        tagCurrentObservation("forecast.days", days);
        tagCurrentObservation("forecast.mode", "single");
        tagCurrentObservation("circuit.state", circuitState.name().toLowerCase(java.util.Locale.ROOT));
        if (isCircuitOpen()) {
            tagCurrentObservation("forecast.outcome", "circuit_open");
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
            tagCurrentObservation("forecast.outcome", "success");
            tagCurrentObservation("forecast.points", response == null || response.forecast() == null
                    ? 0 : response.forecast().size());
            return response;
        } catch (final RestClientResponseException e) {
            tagCurrentObservation("forecast.outcome", "http_error");
            tagCurrentObservation("http.status_code", e.getStatusCode().value());
            log.error("AI forecast HTTP error for productId={}: status={}, body={}",
                    productId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            recordFailure();
            return null;
        } catch (final ResourceAccessException e) {
            tagCurrentObservation("forecast.outcome", "connection_error");
            log.error("AI forecast timeout/connection error for productId={}: {}", productId, e.getMessage());
            recordFailure();
            return null;
        } catch (final Exception e) {
            tagCurrentObservation("forecast.outcome", "error");
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
    @Observed(name = "ai.forecast.client.bulk", contextualName = "ai-forecast-predict-bulk")
    public List<AiForecastResponse> getBulkForecasts(final List<Long> productIds, final int days) {
        tagCurrentObservation("forecast.mode", "bulk");
        tagCurrentObservation("forecast.product_count", productIds.size());
        tagCurrentObservation("forecast.days", days);
        tagCurrentObservation("circuit.state", circuitState.name().toLowerCase(java.util.Locale.ROOT));
        if (isCircuitOpen()) {
            tagCurrentObservation("forecast.outcome", "circuit_open");
            log.warn("AI forecast circuit breaker is OPEN; skipping bulk request for {} products", productIds.size());
            return null;
        }

        final String url = properties.getUrl() + "/predict/bulk";
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.set("X-API-Key", properties.getApiKey());
        }
        final List<AiForecastRequest> products = productIds.stream()
                .map(id -> new AiForecastRequest(id, days))
                .toList();
        final HttpEntity<AiBulkForecastRequest> request =
                new HttpEntity<>(new AiBulkForecastRequest(products), headers);

        try {
            final ResponseEntity<List<AiForecastResponse>> responseEntity = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
            recordSuccess();
            final List<AiForecastResponse> body = responseEntity.getBody();
            tagCurrentObservation("forecast.outcome", "success");
            tagCurrentObservation("forecast.response_count", body == null ? 0 : body.size());
            return responseEntity.getBody();
        } catch (final RestClientResponseException e) {
            tagCurrentObservation("forecast.outcome", "http_error");
            tagCurrentObservation("http.status_code", e.getStatusCode().value());
            log.error("AI bulk forecast HTTP error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            recordFailure();
            return null;
        } catch (final ResourceAccessException e) {
            tagCurrentObservation("forecast.outcome", "connection_error");
            log.error("AI bulk forecast timeout/connection error: {}", e.getMessage());
            recordFailure();
            return null;
        } catch (final Exception e) {
            tagCurrentObservation("forecast.outcome", "error");
            log.error("AI bulk forecast unexpected error", e);
            recordFailure();
            return null;
        }
    }

    private void tagCurrentObservation(final String key, final Object value) {
        final Observation current = observationRegistry.getCurrentObservation();
        if (current != null && value != null) {
            current.highCardinalityKeyValue(key, String.valueOf(value));
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
     * The ai-module (FastAPI/Pydantic) expects snake_case {@code product_id}; without the explicit
     * name Jackson would send {@code productId} and FastAPI rejects the body with HTTP 422.
     *
     * @param productId product identifier
     * @param days      number of days to forecast
     */
    public record AiForecastRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("product_id") Long productId,
            int days) {
    }

    /**
     * Response payload from {@code POST /predict}. The ai-module returns snake_case
     * {@code product_id}, so map it explicitly.
     *
     * @param productId product identifier
     * @param days      number of forecasted days
     * @param forecast  daily forecast points
     */
    public record AiForecastResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("product_id") Long productId,
            int days,
            List<ForecastPoint> forecast) {

        /**
         * Single daily forecast point from Prophet.
         *
         * @param ds        date string (ISO-8601)
         * @param yhat      predicted demand
         * @param yhatLower lower bound of the prediction interval (may be null on older responses)
         * @param yhatUpper upper bound of the prediction interval (may be null on older responses)
         */
        public record ForecastPoint(
                String ds,
                double yhat,
                @com.fasterxml.jackson.annotation.JsonProperty("yhat_lower") Double yhatLower,
                @com.fasterxml.jackson.annotation.JsonProperty("yhat_upper") Double yhatUpper) {

            /**
             * Convenience constructor for points without prediction bounds.
             *
             * @param ds   date string (ISO-8601)
             * @param yhat predicted demand
             */
            public ForecastPoint(final String ds, final double yhat) {
                this(ds, yhat, null, null);
            }
        }
    }

    /**
     * Request payload for {@code POST /predict/bulk}. The ai-module expects a {@code products}
     * array of per-product {@code {product_id, days}} items (not a flat id list), so model it that
     * way and reuse {@link AiForecastRequest} (which already maps {@code product_id}).
     *
     * @param products per-product forecast requests
     */
    public record AiBulkForecastRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("products") List<AiForecastRequest> products) {
    }

    private static final Logger log = LoggerFactory.getLogger(AiForecastClient.class);
}
