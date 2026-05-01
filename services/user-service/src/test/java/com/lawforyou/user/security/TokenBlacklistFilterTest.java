package com.lawforyou.user.security;

import com.lawforyou.user.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenBlacklistFilter}.
 * Uses Spring's {@link MockHttpServletRequest}/{@link MockHttpServletResponse}
 * — no context required.
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistFilterTest {

    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock FilterChain           filterChain;

    @InjectMocks TokenBlacklistFilter filter;

    MockHttpServletRequest  request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void noAuthorizationHeader_passesThrough() throws Exception {
        // No header at all — filter must not touch blacklist and must call chain
        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(tokenBlacklistService);
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void nonBearerAuthorizationHeader_passesThrough() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(tokenBlacklistService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validToken_notBlacklisted_passesThrough() throws Exception {
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        when(tokenBlacklistService.isBlacklisted("valid-jwt-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(tokenBlacklistService).isBlacklisted("valid-jwt-token");
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void blacklistedToken_returns401AndStopsChain() throws Exception {
        request.addHeader("Authorization", "Bearer blacklisted-token");
        when(tokenBlacklistService.isBlacklisted("blacklisted-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(tokenBlacklistService).isBlacklisted("blacklisted-token");
        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("invalidated");
    }
}

