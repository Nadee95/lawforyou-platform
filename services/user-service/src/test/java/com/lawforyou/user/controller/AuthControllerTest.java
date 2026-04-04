package com.lawforyou.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.security.JwtTokenProvider;
import com.lawforyou.user.security.SecurityConfig;
import com.lawforyou.user.security.UserDetailsServiceImpl;
import com.lawforyou.user.service.UserService;
import com.nadeex.spring.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserService userService;

    @MockitoBean UserDetailsServiceImpl userDetailsService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void register_withValidRequest_returns201() throws Exception {
        var request = new RegisterUserRequest(
                "john.doe", "john@acme.com", "Password123",
                "John", "Doe", null);

        var userDto = new UserDto(UUID.randomUUID(), "john.doe", "john@acme.com",
                "John", "Doe", null, true,
                Set.of("CLIENT"), Set.of("CASE_READ"), TENANT_ID, null, null, null, null);

        when(userService.register(any(), any())).thenReturn(userDto);

        mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("john.doe"));
    }

    @Test
    void register_withInvalidEmail_returns422() throws Exception {
        var request = new RegisterUserRequest(
                "john.doe", "not-an-email", "Password123",
                null, null, null);

        mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        var request = new LoginRequest("john.doe", "Password123");

        var userDto = new UserDto(UUID.randomUUID(), "john.doe", "john@acme.com",
                "John", "Doe", null, true,
                Set.of("LAWYER"), Set.of("CASE_READ", "CASE_CREATE"),
                TENANT_ID, null, null, null, null);

        var success = new AuthResult.Success("jwt-token-here", 86400L, userDto);
        when(userService.authenticate(any(), any())).thenReturn(success);

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token-here"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        var request = new LoginRequest("john.doe", "wrong-password");

        when(userService.authenticate(any(), any()))
                .thenReturn(new AuthResult.Failure("Invalid credentials",
                        com.nadeex.spring.common.exception.ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}

