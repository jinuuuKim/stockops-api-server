package com.stockops.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService);
    }

    @Test
    void loginSetsSecureHttpOnlyRefreshCookie() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@stockops.test");
        request.setPassword("password");
        AuthResult authResult = new AuthResult(loginResponse("ADMIN"), "refresh-token", 604800000L);
        when(authService.authenticate(request)).thenReturn(authResult);

        ResponseEntity<LoginResponse> response = authController.login(request);

        assertThat(response.getBody()).isSameAs(authResult.loginResponse());
        assertRefreshCookie(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "refresh-token", "Max-Age=604800");
    }

    @Test
    void refreshUsesRefreshCookieAndRotatesCookieOnSuccess() {
        AuthResult authResult = new AuthResult(loginResponse("ADMIN"), "rotated-refresh-token", 604800000L);
        when(authService.refreshSession("refresh-token")).thenReturn(authResult);

        ResponseEntity<LoginResponse> response = authController.refresh("refresh-token");

        assertThat(response.getBody()).isSameAs(authResult.loginResponse());
        assertRefreshCookie(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "rotated-refresh-token", "Max-Age=604800");
    }

    @Test
    void refreshRejectsMissingCookie() {
        when(authService.refreshSession(null))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh cookie"));

        assertThatThrownBy(() -> authController.refresh(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing refresh cookie");
    }

    @Test
    void refreshRejectsExpiredCookie() {
        when(authService.refreshSession("expired-refresh-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        assertThatThrownBy(() -> authController.refresh("expired-refresh-token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void logoutClearsRefreshCookie() {
        ResponseEntity<Void> response = authController.logout();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertRefreshCookie(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "", "Max-Age=0");
    }

    private static void assertRefreshCookie(final String cookie, final String value, final String maxAge) {
        assertThat(cookie).contains(AuthController.REFRESH_COOKIE_NAME + "=" + value);
        assertThat(cookie).contains("Path=/api/v1/auth");
        assertThat(cookie).contains(maxAge);
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("SameSite=Strict");
    }

    private static LoginResponse loginResponse(final String role) {
        return new LoginResponse(
                "access-token",
                "Bearer",
                900000L,
                new LoginResponse.AuthenticatedUser(
                        1L,
                        "admin@stockops.test",
                        "Admin User",
                        role,
                        List.of("AI_SUGGESTION_READ"),
                        null));
    }
}
