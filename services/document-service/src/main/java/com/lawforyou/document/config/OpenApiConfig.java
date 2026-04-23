package com.lawforyou.document.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 configuration for the Document Service.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "LawForYou — Document Service API",
                version     = "v1",
                description = "Document upload, download, versioning and case linkage"
        )
)
@SecurityScheme(
        name        = "bearerAuth",
        type        = SecuritySchemeType.HTTP,
        scheme      = "bearer",
        bearerFormat= "JWT",
        in          = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}

