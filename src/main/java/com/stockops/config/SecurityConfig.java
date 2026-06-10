package com.stockops.config;

import com.stockops.logging.CrudRequestLoggingFilter;
import com.stockops.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Optional;

/**
 * Spring Security configuration for JWT-protected APIs.
 * Keeps authentication stateless and leaves actuator and API docs publicly accessible.
 *
 * @author StockOps Team
 * @since 1.0
 * @see JwtAuthenticationFilter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Builds the HTTP security filter chain.
     * Adds XSS-prevention and hardening headers alongside JWT authentication.
     *
     * @param http Spring Security HTTP builder
     * @param jwtAuthenticationFilter JWT authentication filter
     * @return configured security filter chain
     * @throws Exception when the filter chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http,
                                           final JwtAuthenticationFilter jwtAuthenticationFilter,
                                           final Optional<RateLimitFilter> rateLimitFilter,
                                           final CrudRequestLoggingFilter crudRequestLoggingFilter,
                                           @Value("${stockops.actuator.prometheus-public:false}")
                                           final boolean prometheusPublic) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configure(http))
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        // X-XSS-Protection is deprecated and disabled.
                        // Modern browsers ignore it; CSP (above) provides actual XSS protection.
                        // See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-XSS-Protection
                        .xssProtection(xss -> xss.disable())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase())))
                .authorizeHttpRequests(auth -> {
                        auth.requestMatchers("/api/v1/auth/**").permitAll()
                            .requestMatchers("/error").permitAll()
                            .requestMatchers("/actuator/health").permitAll();
                        // /actuator/prometheus is public ONLY when the deployment network restricts
                        // access (private subnet, reverse-proxy allowlist, VPN, or scraper network policy).
                        // Default is authenticated so a public internet mirror does not leak metrics.
                        if (prometheusPublic) {
                            auth.requestMatchers("/actuator/prometheus").permitAll();
                        }
                        auth.requestMatchers("/ws/**", "/ws-sockjs/**").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // CRUD logging runs after JWT (so the security context is populated) but before rate limiting
        // (so rate-limited requests are logged with their 429 status).
        http.addFilterAfter(crudRequestLoggingFilter, JwtAuthenticationFilter.class);
        rateLimitFilter.ifPresent(filter -> http.addFilterAfter(filter, CrudRequestLoggingFilter.class));

        return http.build();
    }

    /**
     * Creates the authentication manager used by the login service.
     *
     * @param userDetailsService user lookup service
     * @param passwordEncoder password encoder for credential checks
     * @return configured authentication manager
     */
    @Bean
    public AuthenticationManager authenticationManager(final UserDetailsService userDetailsService,
                                                       final PasswordEncoder passwordEncoder) {
        final DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

    /**
     * Creates the password encoder used for local credentials.
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
