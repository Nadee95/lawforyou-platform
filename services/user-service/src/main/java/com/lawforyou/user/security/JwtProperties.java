package com.lawforyou.user.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Binds JWT configuration from {@code lawforyou.security.jwt.*} in application.yml.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "lawforyou.security.jwt")
public class JwtProperties {

    /** HMAC-SHA256 signing secret. Must be at least 256 bits (32 chars). */
    @NotBlank
    private String secret;

    /** Token validity in milliseconds. Default: 24 h. */
    @Positive
    private long expirationMs = 86_400_000L;

    /** Derived: expiration in seconds (used in LoginResponse). */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}

