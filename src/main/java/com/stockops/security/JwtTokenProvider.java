package com.stockops.security;

import com.stockops.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Provides JWT generation and validation utilities.
 * Tokens use the user's email as the subject and store the user id and role as claims.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Component
public class JwtTokenProvider {

    private static final String USER_ID_CLAIM = "uid";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private SecretKey signingKey;

    /**
     * Initializes the signing key from the configured shared secret.
     * Fails fast if JWT_SECRET is not set or still uses the legacy default value.
     */
    @PostConstruct
    public void initialize() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. "
                    + "The application refuses to start without a JWT secret for security reasons.");
        }
        if ("change-me-in-production".equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is set to the legacy default value 'change-me-in-production'. "
                    + "Please set a strong, unique secret before starting the application.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed JWT access token for the supplied user.
     *
     * @param user authenticated domain user
     * @return signed access token
     */
    public String generateAccessToken(final User user) {
        return generateToken(user, ACCESS_TOKEN_TYPE, expiration);
    }

    public String generateRefreshToken(final User user) {
        return generateToken(user, REFRESH_TOKEN_TYPE, refreshExpiration);
    }

    /**
     * Generates a fresh access token from a previously issued token.
     * Expired access tokens are allowed as long as their signature is still valid.
     *
     * @param token raw JWT access token
     * @return newly issued access token
     */
    public String refreshAccessToken(final String token) {
        final Claims claims = extractClaimsAllowExpired(token);
        final String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        final Number userId = claims.get(USER_ID_CLAIM, Number.class);

        if (!ACCESS_TOKEN_TYPE.equals(tokenType) || userId == null) {
            throw new JwtException("Unsupported token type");
        }

        final Instant now = Instant.now();
        final Instant expiresAt = now.plusMillis(expiration);

        return Jwts.builder()
                .subject(claims.getSubject())
                .claim(USER_ID_CLAIM, userId.longValue())
                .claim(ROLE_CLAIM, claims.get(ROLE_CLAIM, String.class))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a token signature and expiration.
     *
     * @param token raw JWT token
     * @return {@code true} when the token is structurally valid and not expired
     */
    public boolean validateToken(final String token) {
        try {
            final Claims claims = extractClaims(token);
            return ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Extracts the email subject from the token.
     *
     * @param token raw JWT token
     * @return token subject email
     */
    public String extractEmail(final String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extracts the user id claim from the token.
     *
     * @param token raw JWT token
     * @return persisted user id
     */
    public Long extractUserId(final String token) {
        final Number userId = extractClaimsAllowExpired(token).get(USER_ID_CLAIM, Number.class);
        return userId == null ? null : userId.longValue();
    }

    public Long extractRefreshUserId(final String token) {
        final Claims claims = extractClaims(token);
        if (!REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Unsupported token type");
        }

        final Number userId = claims.get(USER_ID_CLAIM, Number.class);
        if (userId == null) {
            throw new JwtException("Missing user id");
        }

        return userId.longValue();
    }

    /**
     * Returns the configured token expiration in milliseconds.
     *
     * @return JWT expiration window in milliseconds
     */
    public long getExpiration() {
        return expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    private String generateToken(final User user, final String tokenType, final long expiresInMillis) {
        final Instant now = Instant.now();
        final Instant expiresAt = now.plusMillis(expiresInMillis);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(USER_ID_CLAIM, user.getId())
                .claim(ROLE_CLAIM, user.getRole().getName())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private Claims extractClaims(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Claims extractClaimsAllowExpired(final String token) {
        try {
            return extractClaims(token);
        } catch (ExpiredJwtException exception) {
            return exception.getClaims();
        }
    }
}
