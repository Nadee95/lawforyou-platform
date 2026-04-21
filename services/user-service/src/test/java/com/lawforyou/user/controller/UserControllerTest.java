package com.lawforyou.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.nadeex.spring.security.config.SecurityAutoConfiguration;
import com.nadeex.spring.security.token.JwtTokenProvider;
import com.nadeex.spring.security.userdetails.TenantAwareUserDetails;
import com.lawforyou.user.security.SecurityConfig;
import com.lawforyou.user.security.UserDetailsServiceImpl;
import com.lawforyou.user.service.UserService;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link UserController}.
 *
 * <p>Key patterns:
 * <ul>
 *   <li>{@code @WithMockUser(authorities = "USER_READ")} — use {@code authorities}, NOT
 *       {@code roles}, because {@link UserController} guards use {@code hasAuthority()}, not
 *       {@code hasRole()}.  Using {@code roles} would silently prefix "ROLE_" and fail.</li>
 *   <li>Owner-access tests use a {@link TenantAwareUserDetails} principal so the SpEL
 *       expression {@code authentication.principal.userId.toString()} resolves correctly.</li>
 * </ul>
 */
@WebMvcTest(UserController.class)
@ImportAutoConfiguration(SecurityAutoConfiguration.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserService            userService;
    @MockitoBean UserDetailsServiceImpl userDetailsService;
    @MockitoBean JwtTokenProvider       jwtTokenProvider;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID ROLE_ID   = UUID.randomUUID();

    private static UserDto sampleUser() {
        return new UserDto(
                USER_ID, "jane.doe", "jane@acme.com",
                "Jane", "Doe", null, true,
                Set.of("LAWYER"), Set.of("CASE_READ", "CASE_CREATE"),
                TENANT_ID, null, null, null, null);
    }

    /** Creates an authenticated token with a TenantAwareUserDetails principal for the given userId. */
    private static UsernamePasswordAuthenticationToken ownerAuth(UUID userId) {
        TenantAwareUserDetails principal = TenantAwareUserDetails.of(userId, TENANT_ID, "owner", List.of("CLIENT"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // ── GET /api/users ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "USER_READ")
    void listUsers_withUserReadAuthority_returns200() throws Exception {
        var paged = PagedResponse.of(List.of(sampleUser()), 0, 20, 1L);
        when(userService.findAll(any(), any())).thenReturn(paged);

        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-ID", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("jane.doe"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser                                  // authenticated but no USER_READ authority
    void listUsers_withoutRequiredAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-ID", TENANT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/users/{userId} ──────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "USER_READ")
    void getUser_withUserReadAuthority_returns200() throws Exception {
        when(userService.findById(eq(USER_ID), any())).thenReturn(sampleUser());

        mockMvc.perform(get("/api/users/{userId}", USER_ID)
                        .header("X-Tenant-ID", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("jane.doe"));
    }

    @Test
    void getUser_asOwner_returns200() throws Exception {
        UUID ownerId = USER_ID;
        var ownerDto = new UserDto(ownerId, "jane.doe", "jane@acme.com",
                "Jane", "Doe", null, true,
                Set.of("CLIENT"), Set.of("CASE_READ"),
                TENANT_ID, null, null, null, null);

        when(userService.findById(eq(ownerId), any())).thenReturn(ownerDto);

        mockMvc.perform(get("/api/users/{userId}", ownerId)
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .with(authentication(ownerAuth(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownerId.toString()));
    }

    // ── PATCH /api/users/{userId} ────────────────────────────────────────────

    @Test
    void updateUser_asOwner_returns200() throws Exception {
        var request = new UpdateUserRequest("Janet", "Smith", null, null);
        var updated = new UserDto(USER_ID, "jane.doe", "jane@acme.com",
                "Janet", "Smith", null, true,
                Set.of("CLIENT"), Set.of("CASE_READ"),
                TENANT_ID, null, null, null, null);

        when(userService.update(eq(USER_ID), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/users/{userId}", USER_ID)
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(ownerAuth(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Janet"))
                .andExpect(jsonPath("$.data.lastName").value("Smith"));
    }

    // ── PATCH /api/users/{userId}/status ────────────────────────────────────

    @Test
    @WithMockUser(authorities = "USER_UPDATE")
    void setUserStatus_deactivate_returns200() throws Exception {
        var deactivated = new UserDto(USER_ID, "jane.doe", "jane@acme.com",
                "Jane", "Doe", null, false,
                Set.of("CLIENT"), Set.of("CASE_READ"),
                TENANT_ID, null, null, null, null);

        when(userService.setActive(eq(USER_ID), any(), eq(false))).thenReturn(deactivated);

        mockMvc.perform(patch("/api/users/{userId}/status", USER_ID)
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // ── POST /api/users/{userId}/roles/{roleId} ──────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_MANAGE")
    void assignRole_withRoleManageAuthority_returns200() throws Exception {
        var withRole = new UserDto(USER_ID, "jane.doe", "jane@acme.com",
                "Jane", "Doe", null, true,
                Set.of("CLIENT", "LAWYER"), Set.of("CASE_READ", "CASE_CREATE"),
                TENANT_ID, null, null, null, null);

        when(userService.assignRole(eq(USER_ID), any(), eq(ROLE_ID))).thenReturn(withRole);

        mockMvc.perform(post("/api/users/{userId}/roles/{roleId}", USER_ID, ROLE_ID)
                        .header("X-Tenant-ID", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2));
    }

    // ── DELETE /api/users/{userId}/roles/{roleId} ────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_MANAGE")
    void removeRole_withRoleManageAuthority_returns200() throws Exception {
        var withoutRole = new UserDto(USER_ID, "jane.doe", "jane@acme.com",
                "Jane", "Doe", null, true,
                Set.of("CLIENT"), Set.of("CASE_READ"),
                TENANT_ID, null, null, null, null);

        when(userService.removeRole(eq(USER_ID), any(), eq(ROLE_ID))).thenReturn(withoutRole);

        mockMvc.perform(delete("/api/users/{userId}/roles/{roleId}", USER_ID, ROLE_ID)
                        .header("X-Tenant-ID", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(1));
    }

    // ── GET /api/users/search ────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "USER_READ")
    void searchUsers_withUserReadAuthority_returns200() throws Exception {
        var paged = PagedResponse.of(List.of(sampleUser()), 0, 20, 1L);
        when(userService.search(any(), eq("jane"), any())).thenReturn(paged);

        mockMvc.perform(get("/api/users/search")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .param("q", "jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("jane@acme.com"));
    }
}

