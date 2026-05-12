package com.lawforyou.user.multitenancy;

import com.nadeex.spring.multitenancy.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantContext} — ThreadLocal set/get/clear semantics.
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_returnsSameTenantId() {
        UUID id = UUID.randomUUID();
        TenantContext.setTenantId(id);
        assertThat(TenantContext.getTenantId()).isEqualTo(id);
    }

    @Test
    void clear_removesStoredTenantId() {
        TenantContext.setTenantId(UUID.randomUUID());
        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void getTenantId_withoutSet_returnsNull() {
        assertThat(TenantContext.getTenantId()).isNull();
    }
}

