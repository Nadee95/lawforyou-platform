package com.lawforyou.cases.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "lawforyou.security.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @Positive
    private long expirationMs = 86_400_000L;
}

