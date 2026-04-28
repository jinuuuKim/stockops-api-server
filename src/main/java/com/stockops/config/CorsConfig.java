package com.stockops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for cross-origin requests.
 * Restricts allowed origins to specific domains for security.
 * Development defaults to {@code http://localhost:5173}.
 * Production origins are configurable via the {@code STOCKOPS_CORS_ALLOWED_ORIGINS}
 * environment variable as a comma-separated list.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origins.
     * Defaults to the local Vite dev server.
     */
    @Value("${stockops.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * Creates CORS configuration source allowing requests from configured origins only.
     * Wildcards are never used; credentials are enabled for explicit origin lists.
     *
     * @return CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
