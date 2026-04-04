package com.lawforyou.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates JWT tokens.
 *
 * <h3>Claims layout:</h3>
 * <pre>
 * {
 *   "sub"         : "&lt;userId UUID&gt;",
 *   "tenantId"    : "&lt;tenantId UUID&gt;",
 *   "username"    : "john.doe",
 *   "roles"       : ["ADMIN","LAWYER"],
 *   "permissions" : ["USER_READ","CASE_CREATE"],
 *   "iat"         : &lt;issued-at epoch seconds&gt;,
 *   "exp"         : &lt;expiry epoch seconds&gt;
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TENANT_ID   = "tenantId";
    private static final String CLAIM_USERNAME    = "username";
    private static final String CLAIM_ROLES       = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";

    private final JwtProperties jwtProperties;

    // ── Token generation ────────────────────────────────────────────────────

    public String generateToken(UUID userId,
                                UUID tenantId,
                                String username,
                                Collection<String> roles,
                                Collection<String> permissions) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TENANT_ID,   tenantId.toString())
                .claim(CLAIM_USERNAME,    username)
                .claim(CLAIM_ROLES,       List.copyOf(roles))
                .claim(CLAIM_PERMISSIONS, List.copyOf(permissions))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    // ── Token validation & claim extraction ─────────────────────────────────

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
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

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        return (List<String>) parseClaims(token).getPayload().get(CLAIM_PERMISSIONS);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

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
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}

