package com.stockops.ai.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AiForecastClientTest {

    private AiForecastProperties properties;
    private AiForecastClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        properties = new AiForecastProperties();
        properties.setUrl("http://localhost:8000");
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setCircuitBreakerFailureThreshold(3);
        properties.setCircuitBreakerCooldown(Duration.ofSeconds(30));
        client = new AiForecastClient(properties, new RestTemplateBuilder());
        mockServer = MockRestServiceServer.createServer(extractRestTemplate(client));
    }

    @Test
    void getForecast_returnsResponseOnSuccess() {
        mockServer.expect(requestTo("http://localhost:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                // The ai-module (FastAPI) requires snake_case product_id; sending productId yields HTTP 422.
                .andExpect(content().json("{\"product_id\":1,\"days\":7}"))
                .andRespond(withSuccess(
                        """
                        {"product_id":1,"days":7,"forecast":[{"ds":"2026-06-01","yhat":10.0,"yhat_lower":8.0,"yhat_upper":12.0}]}
                        """,
                        MediaType.APPLICATION_JSON));

        final AiForecastClient.AiForecastResponse response = client.getForecast(1L, 7);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.days()).isEqualTo(7);
        assertThat(response.forecast()).hasSize(1);
        assertThat(response.forecast().get(0).yhat()).isEqualTo(10.0);
    }

    @Test
    void getForecast_sendsApiKeyHeaderWhenConfigured() {
        properties.setApiKey("test-secret");

        mockServer.expect(requestTo("http://localhost:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-secret"))
                .andRespond(withSuccess(
                        """
                        {"product_id":1,"days":1,"forecast":[{"ds":"2026-06-01","yhat":5.0,"yhat_lower":3.0,"yhat_upper":7.0}]}
                        """,
                        MediaType.APPLICATION_JSON));

        final AiForecastClient.AiForecastResponse response = client.getForecast(1L, 1);

        assertThat(response).isNotNull();
        mockServer.verify();
    }

    @Test
    void getForecast_returnsNullOn401() {
        mockServer.expect(requestTo("http://localhost:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        final AiForecastClient.AiForecastResponse response = client.getForecast(1L, 7);

        assertThat(response).isNull();
    }

    @Test
    void getForecast_returnsNullOn500() {
        mockServer.expect(requestTo("http://localhost:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        final AiForecastClient.AiForecastResponse response = client.getForecast(1L, 7);

        assertThat(response).isNull();
    }

    @Test
    void getForecast_returnsNullOnInvalidJson() {
        mockServer.expect(requestTo("http://localhost:8000/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        final AiForecastClient.AiForecastResponse response = client.getForecast(1L, 7);

        assertThat(response).isNull();
    }

    private RestTemplate extractRestTemplate(final AiForecastClient forecastClient) {
        try {
            final var field = AiForecastClient.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            return (RestTemplate) field.get(forecastClient);
        } catch (final Exception e) {
            throw new RuntimeException("Could not extract restTemplate", e);
        }
    }
}
