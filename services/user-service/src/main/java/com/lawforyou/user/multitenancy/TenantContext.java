package com.lawforyou.user.multitenancy;

import java.util.UUID;

/**
 * Thread-local holder for the current tenant identifier.
 *
 * <p>Set at the start of every HTTP request by {@code TenantResolutionFilter}
 * and cleared in its {@code finally} block so threads returned to the pool
 * are never contaminated with a stale tenant.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

