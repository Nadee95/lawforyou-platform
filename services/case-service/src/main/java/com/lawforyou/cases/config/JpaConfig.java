package com.lawforyou.cases.config;

import com.lawforyou.cases.multitenancy.TenantIdentifierResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link TenantIdentifierResolver} with Hibernate so
 * {@code @TenantId} discrimination works at runtime.
 */
@Configuration
@RequiredArgsConstructor
public class JpaConfig {

    private final TenantIdentifierResolver tenantIdentifierResolver;

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return properties ->
                properties.put("hibernate.tenant_identifier_resolver", tenantIdentifierResolver);
    }
}

