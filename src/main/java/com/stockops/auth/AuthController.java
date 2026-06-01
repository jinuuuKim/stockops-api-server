package com.stockops.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Authentication API controller.
 * Provides login, token refresh, and stateless logout endpoints.
 *
 * @author StockOps Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE_NAME = "stockops_refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String REFRESH_COOKIE_SAME_SITE = "Strict";

    private final AuthService authService;

    @Value("${stockops.auth.refresh-cookie.secure:true}")
    private boolean refreshCookieSecure = true;

    /**
     * Creates the authentication controller.
     *
     * @param authService authentication service
     */
    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user and returns a JWT access token.
     *
     * @param loginRequest login request payload
     * @return JWT access token response
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody final LoginRequest loginRequest) {
        final AuthResult authResult = authService.authenticate(loginRequest);
        return withRefreshCookie(authResult);
    }

    /**
     * Issues a new JWT access token from the HttpOnly refresh cookie.
     *
     * @param refreshToken refresh-cookie token
     * @return refreshed JWT access token response
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) final String refreshToken) {
        final AuthResult authResult = authService.refreshSession(refreshToken);
        return withRefreshCookie(authResult);
    }

    /**
     * Completes stateless logout.
     * Clients should discard the bearer token on receipt of this response.
     *
     * @return empty successful response
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(final AuthResult authResult) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(authResult).toString())
                .body(authResult.loginResponse());
    }

    private ResponseCookie refreshCookie(final AuthResult authResult) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, authResult.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(REFRESH_COOKIE_SAME_SITE)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(authResult.refreshExpiresIn()))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(REFRESH_COOKIE_SAME_SITE)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
