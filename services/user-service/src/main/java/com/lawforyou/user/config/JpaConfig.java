package com.lawforyou.user.config;

import com.nadeex.spring.multitenancy.hibernate.TenantIdentifierResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link TenantIdentifierResolver} with Hibernate so that
 * the {@code SessionFactory} knows how to resolve the current tenant when
 * the {@code @TenantId} discriminator is in use.
 *
 * <p>Without this, Hibernate detects multi-tenancy (because of {@code @TenantId}
 * on {@link com.lawforyou.user.entity.User}) but has no resolver, and throws
 * "SessionFactory configured for multi-tenancy, but no tenant identifier specified"
 * at startup.</p>
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

