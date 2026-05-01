package com.lawforyou.user.multitenancy;

import com.nadeex.spring.security.token.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantResolutionFilter}.
 *
 * <p>Verifies all tenant resolution branches:
 * <ol>
 *   <li>Valid {@code X-Tenant-ID} header</li>
 *   <li>Invalid {@code X-Tenant-ID} header (not a UUID)</li>
 *   <li>No header — falls back to JWT claim</li>
 *   <li>No header, invalid JWT — tenant not set</li>
 *   <li>No header, no JWT — tenant not set</li>
 *   <li>TenantContext is always cleared after filter runs</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock FilterChain      filterChain;

    @InjectMocks TenantResolutionFilter filter;

    MockHttpServletRequest  request;
    MockHttpServletResponse response;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        TenantContext.clear(); // start each test clean
    }

    @Test
    void validTenantHeader_setsTenantContext() throws Exception {
        request.addHeader("X-Tenant-ID", TENANT_ID.toString());

        // capture context INSIDE the filter chain call
        doAnswer(inv -> {
            assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_ID);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidTenantHeader_doesNotSetContext_andPassesThrough() throws Exception {
        request.addHeader("X-Tenant-ID", "not-a-uuid");

        doAnswer(inv -> {
            assertThat(TenantContext.getTenantId()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noHeader_validJwt_setsTenantFromJwtClaim() throws Exception {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.isTokenValid(token)).thenReturn(true);
        when(jwtTokenProvider.getTenantId(token)).thenReturn(TENANT_ID);

        doAnswer(inv -> {
            assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_ID);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noHeader_invalidJwt_doesNotSetContext() throws Exception {
        String token = "bad.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.isTokenValid(token)).thenReturn(false);

        doAnswer(inv -> {
            assertThat(TenantContext.getTenantId()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noHeader_jwtThrowsException_doesNotSetContext() throws Exception {
        String token = "throwing.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.isTokenValid(token)).thenThrow(new RuntimeException("parse error"));

        doAnswer(inv -> {
            assertThat(TenantContext.getTenantId()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noHeaderNoJwt_doesNotSetContext() throws Exception {
        // completely empty request
        doAnswer(inv -> {
            assertThat(TenantContext.getTenantId()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tenantContext_isAlwaysClearedAfterFilter() throws Exception {
        request.addHeader("X-Tenant-ID", TENANT_ID.toString());

        filter.doFilterInternal(request, response, filterChain);

        // After filter completes, context must be cleared regardless
        assertThat(TenantContext.getTenantId()).isNull();
    }
}

