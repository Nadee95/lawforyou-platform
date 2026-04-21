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
import java.util.List;
import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_USERNAME  = "username";
    private static final String CLAIM_ROLES     = "roles";
    private final JwtProperties jwtProperties;
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