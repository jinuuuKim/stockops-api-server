package com.stockops.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Servlet filter that logs CRUD API requests and responses with operation-aware log levels.
 *
 * <p>This filter is registered in the Spring Security filter chain immediately after
 * {@code JwtAuthenticationFilter} so the authenticated user is already available in
 * {@code SecurityContextHolder} when the log line is emitted.
 *
 * <h3>Log level matrix</h3>
 * <table>
 *   <tr><th>HTTP method</th><th>Status</th><th>Operation</th><th>Level</th></tr>
 *   <tr><td>GET / HEAD</td><td>2xx</td><td>READ</td><td>DEBUG</td></tr>
 *   <tr><td>POST</td><td>2xx</td><td>CREATE</td><td>INFO</td></tr>
 *   <tr><td>PUT / PATCH</td><td>2xx</td><td>UPDATE</td><td>INFO</td></tr>
 *   <tr><td>DELETE</td><td>2xx</td><td>DELETE</td><td>WARN</td></tr>
 *   <tr><td>any</td><td>4xx</td><td>—</td><td>WARN</td></tr>
 *   <tr><td>any</td><td>5xx</td><td>—</td><td>ERROR</td></tr>
 * </table>
 *
 * <h3>Log format</h3>
 * <pre>
 * [CRUD] {OPERATION} {METHOD} {path}[?query] → {status} | user={email} | {N}ms | ip={ip}
 *        [| req={body}] [| res={body}]
 * </pre>
 *
 * <h3>Excluded paths</h3>
 * <ul>
 *   <li>{@code /api/v1/auth/**} — login request bodies contain credentials</li>
 *   <li>{@code /actuator}, {@code /swagger-ui}, {@code /v3/api-docs} — infrastructure</li>
 *   <li>{@code /ws}, {@code /ws-sockjs} — WebSocket upgrade requests</li>
 *   <li>{@code /error} — Spring error dispatcher</li>
 * </ul>
 *
 * <h3>Body handling</h3>
 * Request bodies are logged for POST/PUT/PATCH only (GET/DELETE have no meaningful body).
 * Response bodies are always logged. Both are truncated at {@value #MAX_BODY_LOG_SIZE} characters
 * and sensitive field values (password, token, etc.) are replaced with {@code ***}.
 *
 * @author StockOps Team
 * @since 1.0
 * @see CrudOperation
 */
@Component
public class CrudRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrudRequestLoggingFilter.class);

    /** Maximum UTF-8 characters of any single body snippet included in a log line. */
    private static final int MAX_BODY_LOG_SIZE = 512;

    /**
     * Path prefixes that are never logged.
     * Auth paths contain credentials; others are non-business infrastructure.
     */
    private static final List<String> SKIP_PATH_PREFIXES = List.of(
            "/api/v1/auth/",
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/ws",
            "/ws-sockjs",
            "/error"
    );

    /**
     * JSON field names whose string values are replaced with {@code ***} before logging.
     * Matching is case-insensitive and applies to both request and response bodies.
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "temporaryPassword", "secret", "token",
            "accessToken", "refreshToken", "apiKey", "webhookUrl",
            "authorization", "authToken", "idToken"
    );

    /**
     * Wraps the request and response with caching wrappers so body content can be read
     * after the filter chain completes, then delegates to the chain and logs the outcome.
     *
     * @param request     inbound HTTP request
     * @param response    outbound HTTP response
     * @param filterChain remaining filter chain
     * @throws ServletException if a filter in the chain throws
     * @throws IOException      if request or response I/O fails
     */
    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String path = request.getRequestURI();

        if (!shouldLog(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        final ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        final ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        final long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            final long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logRequest(wrappedRequest, wrappedResponse, durationMs, path);
            // Flush the buffered response body to the actual client response.
            wrappedResponse.copyBodyToResponse();
        }
    }

    // -----------------------------------------------------------------------
    // Routing
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when the path starts with {@code /api/v1/} and is not in the skip list.
     *
     * @param path request URI
     * @return whether this request should be logged
     */
    private boolean shouldLog(final String path) {
        if (!path.startsWith("/api/v1/")) {
            return false;
        }
        return SKIP_PATH_PREFIXES.stream().noneMatch(path::startsWith);
    }

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------

    private void logRequest(final ContentCachingRequestWrapper request,
                            final ContentCachingResponseWrapper response,
                            final long durationMs,
                            final String path) {
        final String method      = request.getMethod();
        final CrudOperation op   = CrudOperation.from(method);
        final int status         = response.getStatus();
        final String fullPath    = buildFullPath(path, request.getQueryString());
        final String user        = resolveUser();
        final String clientIp    = resolveClientIp(request);
        final String requestBody = extractRequestBody(request, method);
        final String responseBody = extractResponseBody(response);

        final String message = buildMessage(op, method, fullPath, status,
                user, durationMs, clientIp, requestBody, responseBody);

        emit(op, status, message);
    }

    private String buildFullPath(final String path, final String queryString) {
        return (queryString != null && !queryString.isBlank()) ? path + '?' + queryString : path;
    }

    private String buildMessage(final CrudOperation operation, final String method, final String fullPath,
                                final int status, final String user, final long durationMs,
                                final String clientIp, final String requestBody, final String responseBody) {
        final StringBuilder sb = new StringBuilder(512);
        sb.append("[CRUD] ").append(operation.label())
          .append(' ').append(method)
          .append(' ').append(fullPath)
          .append(" → ").append(status)
          .append(" | user=").append(user)
          .append(" | ").append(durationMs).append("ms")
          .append(" | ip=").append(clientIp);

        if (!requestBody.isEmpty()) {
            sb.append(" | req=").append(requestBody);
        }
        if (!responseBody.isEmpty()) {
            sb.append(" | res=").append(responseBody);
        }

        return sb.toString();
    }

    /**
     * Selects the SLF4J log level based on response status code and CRUD operation.
     *
     * <p>HTTP status takes priority over operation type:
     * 5xx → ERROR, 4xx → WARN. For 2xx/3xx responses the operation decides:
     * READ → DEBUG, DELETE → WARN, CREATE/UPDATE → INFO.
     *
     * @param operation CRUD operation derived from HTTP method
     * @param status    HTTP response status code
     * @param message   formatted log line to emit
     */
    private void emit(final CrudOperation operation, final int status, final String message) {
        if (status >= 500) {
            LOGGER.error(message);
            return;
        }
        if (status >= 400) {
            LOGGER.warn(message);
            return;
        }
        switch (operation) {
            case READ   -> LOGGER.debug(message);
            case DELETE -> LOGGER.warn(message);
            default     -> LOGGER.info(message);
        }
    }

    // -----------------------------------------------------------------------
    // Body extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts and redacts the cached request body for mutation methods (POST/PUT/PATCH).
     * Returns empty string for GET, HEAD, and DELETE — their bodies are not meaningful.
     *
     * @param request cached request wrapper
     * @param method  HTTP method
     * @return redacted, truncated body snippet; or empty string
     */
    private String extractRequestBody(final ContentCachingRequestWrapper request, final String method) {
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            return "";
        }
        final byte[] content = request.getContentAsByteArray();
        return content.length == 0 ? "" : formatBody(content);
    }

    /**
     * Extracts and redacts the cached response body.
     *
     * @param response cached response wrapper
     * @return redacted, truncated body snippet; or empty string
     */
    private String extractResponseBody(final ContentCachingResponseWrapper response) {
        final byte[] content = response.getContentAsByteArray();
        return content.length == 0 ? "" : formatBody(content);
    }

    /**
     * Converts a raw body byte array to a log-safe string: decoded as UTF-8,
     * truncated at {@value #MAX_BODY_LOG_SIZE} characters, and sensitive values masked.
     *
     * @param content raw body bytes
     * @return log-safe string representation
     */
    private String formatBody(final byte[] content) {
        final int length = Math.min(content.length, MAX_BODY_LOG_SIZE);
        final String raw = new String(content, 0, length, StandardCharsets.UTF_8);
        final String masked = maskSensitiveFields(raw);
        return content.length > MAX_BODY_LOG_SIZE ? masked + "…(truncated)" : masked;
    }

    /**
     * Replaces string values of known-sensitive JSON fields with {@code ***}.
     * Only handles simple {@code "field":"value"} patterns; deeply nested structures are truncated
     * by {@link #MAX_BODY_LOG_SIZE} before they reach this method.
     *
     * @param body raw body snippet
     * @return body with sensitive values replaced
     */
    private String maskSensitiveFields(final String body) {
        return maskSensitiveFieldsStatic(body);
    }

    /**
     * Test-visible entry point for {@link #maskSensitiveFieldsStatic(String)} so unit tests can
     * exercise the masking logic without constructing a servlet request/response pair.
     *
     * @param body raw body snippet
     * @return body with sensitive values replaced
     */
    static String maskForTest(final String body) {
        return maskSensitiveFieldsStatic(body);
    }

    private static String maskSensitiveFieldsStatic(final String body) {
        String masked = body;
        for (final String field : SENSITIVE_FIELDS) {
            masked = masked.replaceAll(
                    "(?i)(\"" + field + "\"\\s*:\\s*\")([^\"]*)(\")",
                    "\"" + field + "\":\"***\""
            );
        }
        return masked;
    }

    // -----------------------------------------------------------------------
    // Context resolution
    // -----------------------------------------------------------------------

    /**
     * Returns the authenticated user's name (typically email) from {@code SecurityContextHolder},
     * or {@code "anonymous"} when the request is unauthenticated.
     *
     * <p>This filter runs after {@code JwtAuthenticationFilter} in the security chain,
     * so the context is already populated for authenticated requests.
     *
     * @return user identifier string
     */
    private String resolveUser() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())
                || auth.getName() == null) {
            return "anonymous";
        }
        return auth.getName();
    }

    /**
     * Resolves the client IP address, honouring {@code X-Forwarded-For} when present
     * (e.g. behind a reverse proxy or load balancer).
     *
     * @param request HTTP request
     * @return client IP address string
     */
    private String resolveClientIp(final HttpServletRequest request) {
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
