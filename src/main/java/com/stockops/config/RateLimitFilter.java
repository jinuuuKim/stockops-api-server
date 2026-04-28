package com.stockops.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servlet filter that enforces API rate limits using Bucket4j with Redis.
 * <p>
 * Three tiers of rate limiting are applied:
 * <ul>
 *   <li>Authenticated users: 100 req/min per user ID</li>
 *   <li>Anonymous users: 10 req/min per client IP</li>
 *   <li>Login endpoint: 5 req/min per client IP</li>
 * </ul>
 * <p>
 * Health check and API documentation endpoints are exempt.
 * On rate limit exceeded, returns 429 with {@code Retry-After} header.
 *
 * @author StockOps Team
 * @since 1.0
 * @see RateLimitConfig
 */
@Component
@ConditionalOnProperty(name = "stockops.ratelimit.enabled", havingValue = "true")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final List<String> EXEMPT_PATHS = List.of(
            "/actuator",
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/api/health"
    );

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final ProxyManager<String> proxyManager;
    private final Bandwidth authenticatedBandwidth;
    private final Bandwidth anonymousBandwidth;
    private final Bandwidth loginBandwidth;

    /**
     * Creates the rate limit filter.
     *
     * @param proxyManager distributed token bucket proxy manager
     * @param authenticatedBandwidth bandwidth for authenticated users
     * @param anonymousBandwidth bandwidth for anonymous users
     * @param loginBandwidth bandwidth for login endpoint
     */
    public RateLimitFilter(final ProxyManager<String> proxyManager,
                           final Bandwidth authenticatedBandwidth,
                           final Bandwidth anonymousBandwidth,
                           final Bandwidth loginBandwidth) {
        this.proxyManager = proxyManager;
        this.authenticatedBandwidth = authenticatedBandwidth;
        this.anonymousBandwidth = anonymousBandwidth;
        this.loginBandwidth = loginBandwidth;
    }

    /**
     * Applies rate limiting to the request. Exempt paths are skipped.
     * On success, adds {@code X-RateLimit-Limit} and {@code X-RateLimit-Remaining}
     * headers. On limit exceeded, returns 429 with {@code Retry-After}.
     *
     * @param request     HTTP request
     * @param response    HTTP response
     * @param filterChain remaining filter chain
     * @throws ServletException if the filter chain fails
     * @throws IOException      if request processing fails
     */
    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String path = request.getRequestURI();

        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String key = resolveKey(request, path);
            final Bandwidth bandwidth = resolveBandwidth(request, path);
            final long capacity = resolveCapacity(request, path);

            final BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(bandwidth)
                    .build();

            final Bucket bucket = proxyManager.builder().build(key, configuration);

            final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                final long waitSeconds = Math.max(1,
                        TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("Retry-After", String.valueOf(waitSeconds));
                response.getWriter().write("Too Many Requests");
            }
        } catch (final Exception e) {
            LOGGER.warn("Rate limiting check failed for {} {}, proceeding without rate limit. Error: {}",
                    request.getMethod(), path, e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    private boolean isExempt(final String path) {
        return EXEMPT_PATHS.stream().anyMatch(path::startsWith);
    }

    private String resolveKey(final HttpServletRequest request, final String path) {
        if (LOGIN_PATH.equals(path)) {
            return "login:" + getClientIp(request);
        }

        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "auth:" + auth.getName();
        }

        return "anon:" + getClientIp(request);
    }

    private Bandwidth resolveBandwidth(final HttpServletRequest request, final String path) {
        if (LOGIN_PATH.equals(path)) {
            return loginBandwidth;
        }

        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return authenticatedBandwidth;
        }

        return anonymousBandwidth;
    }

    private long resolveCapacity(final HttpServletRequest request, final String path) {
        if (LOGIN_PATH.equals(path)) {
            return RateLimitConfig.LOGIN_CAPACITY;
        }

        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return RateLimitConfig.AUTHENTICATED_CAPACITY;
        }

        return RateLimitConfig.ANONYMOUS_CAPACITY;
    }

    private String getClientIp(final HttpServletRequest request) {
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
