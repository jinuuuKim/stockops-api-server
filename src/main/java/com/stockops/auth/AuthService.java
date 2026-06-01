package com.stockops.auth;

import com.stockops.entity.User;
import com.stockops.repository.RolePermissionRepository;
import com.stockops.repository.UserRepository;
import com.stockops.security.JwtTokenProvider;
import com.stockops.security.ScopeAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication business logic for login and token renewal.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ScopeAccessService scopeAccessService;

    /**
     * Creates the authentication service.
     *
     * @param authenticationManager Spring Security authentication manager
     * @param userRepository user repository
     * @param rolePermissionRepository role-permission repository
     * @param jwtTokenProvider JWT utility component
     */
    public AuthService(final AuthenticationManager authenticationManager,
                       final UserRepository userRepository,
                       final RolePermissionRepository rolePermissionRepository,
                       final JwtTokenProvider jwtTokenProvider,
                       final ScopeAccessService scopeAccessService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.scopeAccessService = scopeAccessService;
    }

    /**
     * Authenticates a user and returns a signed access token.
     *
     * @param loginRequest login request payload
     * @return signed access token response
     * @throws ResponseStatusException when the credentials are invalid
     */
    public AuthResult authenticate(final LoginRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password", exception);
        }

        final User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return buildResult(user);
    }

    /**
     * Refreshes an access token from the supplied refresh-cookie token.
     *
     * @param refreshToken HttpOnly refresh cookie value
     * @return newly issued access-token response and rotated refresh token
     * @throws ResponseStatusException when the cookie is missing or the token is invalid
     */
    public AuthResult refreshSession(final String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh cookie");
        }

        try {
            final Long userId = jwtTokenProvider.extractRefreshUserId(refreshToken);
            final User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
            return buildResult(user);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", exception);
        }
    }

    private AuthResult buildResult(final User user) {
        return new AuthResult(
                buildResponse(jwtTokenProvider.generateAccessToken(user), user),
                jwtTokenProvider.generateRefreshToken(user),
                jwtTokenProvider.getRefreshExpiration());
    }

    private LoginResponse buildResponse(final String accessToken, final User user) {
        final var scopeMetadata = scopeAccessService.buildUserProfile(user).toDto();
        return new LoginResponse(
                accessToken,
                TOKEN_TYPE,
                jwtTokenProvider.getExpiration(),
                new LoginResponse.AuthenticatedUser(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getRole().getName(),
                        rolePermissionRepository.findPermissionCodesByRoleId(user.getRole().getId()),
                        scopeMetadata));
    }

}
