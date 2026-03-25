package com.mentorplatform.mentor_platform.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT utility for the 1-on-1 Mentor–Student Platform.
 *
 * Responsibilities:
 *  - Generate access tokens  (short-lived, e.g. 15 min)
 *  - Generate refresh tokens (long-lived, e.g. 7 days)
 *  - Validate tokens
 *  - Extract claims (subject, role, expiry …)
 *
 * Required application.properties keys:
 *   jwt.secret          = <256-bit base64 or plain string, min 32 chars>
 *   jwt.expiration      = 900000        # 15 minutes in ms
 *   jwt.refresh-expiration = 604800000  # 7 days in ms
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    // ---------------------------------------------------------------
    // Configuration (injected from application.properties)
    // ---------------------------------------------------------------

    @Value("${jwt.secret}")
    private String secret;

    /** Access token lifetime in milliseconds (default 15 min). */
    @Value("${jwt.expiration:900000}")
    private long accessTokenExpiry;

    /** Refresh token lifetime in milliseconds (default 7 days). */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiry;

    // ---------------------------------------------------------------
    // Key derivation
    // ---------------------------------------------------------------

    /**
     * Derives an HMAC-SHA256 {@link SecretKey} from the configured secret.
     * Called lazily on every operation – Spring caches the bean so it is
     * effectively computed once per application lifecycle.
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------
    // Token generation
    // ---------------------------------------------------------------

    /**
     * Generates a signed access JWT for the given user.
     *
     * @param userDetails Spring Security principal
     * @param role        "MENTOR" or "STUDENT"
     * @return compact JWT string
     */
    public String generateAccessToken(UserDetails userDetails, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "access");
        return buildToken(claims, userDetails.getUsername(), accessTokenExpiry);
    }

    /**
     * Generates a signed refresh JWT.
     * Refresh tokens carry minimal claims – only subject and type.
     *
     * @param userDetails Spring Security principal
     * @return compact JWT string
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userDetails.getUsername(), refreshTokenExpiry);
    }

    /**
     * Regenerates an access token using a valid refresh token.
     * Validates that the provided token is actually a refresh token.
     *
     * @param refreshToken existing refresh JWT
     * @param userDetails  principal loaded from the DB for double-check
     * @param role         current role for the new access token
     * @return new access JWT, or {@code null} if the refresh token is invalid
     */
    public String refreshAccessToken(String refreshToken,
                                     UserDetails userDetails,
                                     String role) {
        try {
            String tokenType = extractClaim(refreshToken,
                    c -> c.get("type", String.class));
            if (!"refresh".equals(tokenType)) {
                log.warn("refreshAccessToken: supplied token is not a refresh token");
                return null;
            }
            if (!isTokenValid(refreshToken, userDetails)) {
                return null;
            }
            return generateAccessToken(userDetails, role);
        } catch (JwtException e) {
            log.warn("refreshAccessToken: {}", e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    /**
     * Returns {@code true} when the token is correctly signed,
     * not expired, and belongs to the given user.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername())
                    && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates the token structure and signature only (no user context).
     * Useful in filter chains before the UserDetails are loaded.
     *
     * @return {@code true} if the token parses and verifies correctly
     */
    public boolean isTokenStructureValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token structure invalid: {}", e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Claims extraction
    // ---------------------------------------------------------------

    /** Extracts the username (subject) from the token. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the role claim ("MENTOR" / "STUDENT"). */
    public String extractRole(String token) {
        return extractClaim(token, c -> c.get("role", String.class));
    }

    /** Extracts the token type claim ("access" / "refresh"). */
    public String extractTokenType(String token) {
        return extractClaim(token, c -> c.get("type", String.class));
    }

    /** Extracts the expiration date from the token. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Returns {@code true} if the token's expiry is in the past. */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generic claims extractor.
     *
     * @param token          compact JWT string
     * @param claimsResolver function mapping {@link Claims} → T
     * @param <T>            return type
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = parseClaims(token);
        return claimsResolver.apply(claims);
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private String buildToken(Map<String, Object> extraClaims,
                              String subject,
                              long expiryMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMillis))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Parses and verifies the token signature.
     * Throws a {@link JwtException} subclass on any failure:
     *   {@link ExpiredJwtException}   – token past its expiry date
     *   {@link SignatureException}    – tampered or wrong secret
     *   {@link MalformedJwtException} – not a valid JWT structure
     *   {@link UnsupportedJwtException} – algorithm mismatch
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}