package com.lawforyou.document.multitenancy;

import java.util.UUID;

/**
 * Thread-local tenant context for the Document Service.
 * Populated by {@link TenantResolutionFilter} at the start of every request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() { throw new UnsupportedOperationException("Utility class"); }

    public static void setTenantId(UUID tenantId) { CURRENT_TENANT.set(tenantId); }
    public static UUID getTenantId()               { return CURRENT_TENANT.get(); }
    public static void clear()                     { CURRENT_TENANT.remove(); }
}

