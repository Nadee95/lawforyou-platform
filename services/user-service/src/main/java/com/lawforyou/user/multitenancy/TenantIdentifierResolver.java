package com.lawforyou.user.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hibernate 6 hook that supplies the current tenant identifier to every
 * Hibernate session opened during an HTTP request.
 *
 * <p>Reads from {@link TenantContext}, which is populated by
 * {@code TenantResolutionFilter} at the very start of each request.
 * When no tenant is active (startup validation, health probes, etc.)
 * a sentinel UUID is returned so Hibernate can initialise without error;
 * {@link #validateExistingCurrentSessions()} returns {@code false} so
 * previously opened sessions are not re-validated against the sentinel.</p>
 *
 * <p>Registered with Hibernate via {@code JpaConfig}.</p>
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /**
     * Sentinel used during startup / health checks when no real tenant is active.
     * No real row will ever carry this value, so queries return safely empty.
     */
    static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}

