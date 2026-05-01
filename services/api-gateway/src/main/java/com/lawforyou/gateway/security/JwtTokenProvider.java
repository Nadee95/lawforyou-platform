package com.lawforyou.gateway.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Validates and generates HS256 (HMAC-SHA256) JWTs using the shared {@code app.jwt.secret}.
 *
 * <p>In Phase 3, {@code generateToken} is used by {@code DualTokenAuthenticationFilter} to
 * create a short-lived internal gateway token passed to downstream services ? regardless of
 * whether the original client token was Keycloak RS256 or legacy HS256. Downstream services
 * always receive a valid HS256 token they can verify with their existing configuration.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_USERNAME  = "username";
    private static final String CLAIM_ROLES     = "roles";

    private final JwtProperties jwtProperties;

    // ?? Validation ????????????????????????????????????????????????????????????

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
        }
        return false;
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getPayload().getSubject());
    }

    public UUID getTenantId(String token) {
        return UUID.fromString(getClaimAsString(token, CLAIM_TENANT_ID));
    }

    public String getUsername(String token) {
        return getClaimAsString(token, CLAIM_USERNAME);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return (List<String>) parseClaims(token).getPayload().get(CLAIM_ROLES);
    }

    // ?? Generation (used by DualTokenAuthenticationFilter for internal tokens) ?

    /**
     * Generates a short-lived HS256 gateway-internal token.
     * Downstream services validate this token with their existing nadeex-spring-security setup.
     *
     * @param userId   PostgreSQL user UUID string
     * @param tenantId tenant UUID string
     * @param username display username (may be null)
     * @param roles    list of role names (may be null)
     */
    public String generateToken(String userId, String tenantId, String username, List<String> roles) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_USERNAME,  username != null ? username : "")
                .claim(CLAIM_ROLES,     roles    != null ? roles    : List.of())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    // ?? Helpers ???????????????????????????????????????????????????????????????

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token);
    }

    private String getClaimAsString(String token, String claimName) {
        return (String) parseClaims(token).getPayload().get(claimName);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}