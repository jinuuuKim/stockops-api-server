package com.stockops.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the actuator and metrics exposure decisions made in {@link SecurityConfig}.
 *
 * <p>Chosen policy: {@code /actuator/health} is public for load balancers, but
 * {@code /actuator/prometheus} requires authentication by default
 * ({@code stockops.actuator.prometheus-public} is {@code false}) so a public mirror does not
 * leak operational metrics. Deployments behind private networking can opt in to public scraping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        // Reachable without a JWT. Security permits it, so the status reflects health state
        // (200 UP or 503 DOWN) rather than 401/403 — both prove public access.
        final int httpStatus = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getStatus();
        assertThat(httpStatus).isIn(200, 503);
    }

    @Test
    void prometheusEndpointRequiresAuthenticationByDefault() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }
}
