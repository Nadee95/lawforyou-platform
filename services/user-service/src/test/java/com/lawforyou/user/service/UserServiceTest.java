package com.lawforyou.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.entity.OutboxEvent;
import com.lawforyou.user.event.UserCreatedEvent;
import com.lawforyou.user.event.UserUpdatedEvent;
import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.SystemRole;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.mapper.UserMapper;
import com.lawforyou.user.repository.OutboxEventRepository;
import com.lawforyou.user.repository.RoleRepository;
import com.lawforyou.user.repository.UserRepository;
import com.lawforyou.user.security.JwtProperties;
import com.lawforyou.user.security.JwtTokenProvider;
import com.lawforyou.user.service.impl.UserServiceImpl;
import com.nadeex.spring.exception.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository    userRepository;
    @Mock RoleRepository    roleRepository;
    @Mock
    OutboxEventRepository outboxEventRepository;
    @Mock UserMapper        userMapper;
    @Mock ObjectMapper      objectMapper;
    @Mock PasswordEncoder   passwordEncoder;
    @Mock JwtTokenProvider  jwtTokenProvider;
    @Mock JwtProperties     jwtProperties;
    @Mock EntityManager     entityManager;


    @InjectMocks UserServiceImpl userService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void register_whenEmailAlreadyExists_throwsConflict() {
        var request = new RegisterUserRequest(
                "john", "john@acme.com", "pass123", null, null, null);

        when(userRepository.existsByEmailAndTenantId("john@acme.com", TENANT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.register(request, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("john@acme.com");

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void register_withValidRequest_savesUserAndWritesOutboxEvent() throws JsonProcessingException {
        var request = new RegisterUserRequest(
                "john", "john@acme.com", "pass123", "John", "Doe", null);

        var clientRole = Role.builder()
                .id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName())
                .systemRole(true)
                .build();

        var savedUser = User.builder()
                .id(UUID.randomUUID())
                .username("john")
                .email("john@acme.com")
                .passwordHash("hashed")
                .roles(Set.of(clientRole))
                .build();

        when(userRepository.existsByEmailAndTenantId(any(), any())).thenReturn(false);
        when(userRepository.existsByUsernameAndTenantId(any(), any())).thenReturn(false);
        when(roleRepository.findByNameAndTenantIdIsNull(SystemRole.CLIENT.roleName()))
                .thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");   // ← toJson() mock
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.register(request, TENANT_ID);

        verify(entityManager).refresh(savedUser);
        verify(userRepository).saveAndFlush(any(User.class));

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(UserCreatedEvent.class.getName());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(savedUser.getId().toString());
    }

    @Test
    void update_withValidRequest_updatesUserAndWritesOutboxEvent() throws JsonProcessingException {
        var role    = Role.builder().id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName()).systemRole(true).build();
        var user    = User.builder().id(UUID.randomUUID()).username("john")
                .email("john@acme.com").passwordHash("hashed")
                .active(true).roles(Set.of(role)).tenantId(TENANT_ID).build();  // ← tenantId set
        var request = new UpdateUserRequest("Janet", "Smith", null, null);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.update(user.getId(), TENANT_ID, request);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(UserUpdatedEvent.class.getName());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void setActive_deactivatesUserAndWritesOutboxEvent() throws JsonProcessingException {
        var role = Role.builder().id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName()).systemRole(true).build();
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .email("john@acme.com").passwordHash("hashed")
                .active(true).roles(Set.of(role)).tenantId(TENANT_ID).build();  // ← tenantId set

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setActive(user.getId(), TENANT_ID, false);

        assertThat(user.isActive()).isFalse();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(UserUpdatedEvent.class.getName());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void authenticate_withWrongPassword_returnsFailure() {
        var request = new LoginRequest("john", "wrong");
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("john")
                .passwordHash("hashed")
                .active(true)
                .roles(Set.of())
                .build();

        when(userRepository.findByUsernameAndTenantId("john", TENANT_ID))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        AuthResult result = userService.authenticate(request, TENANT_ID);

        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void authenticate_withCorrectCredentials_returnsSuccess() {
        var request = new LoginRequest("john", "correct");
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("john")
                .passwordHash("hashed")
                .active(true)
                .roles(Set.of())
                .build();

        when(userRepository.findByUsernameAndTenantId("john", TENANT_ID))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any()))
                .thenReturn("jwt-token");
        when(jwtProperties.getExpirationSeconds()).thenReturn(86400L);
        when(userMapper.toDto(user)).thenReturn(null);

        AuthResult result = userService.authenticate(request, TENANT_ID);

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(((AuthResult.Success) result).token()).isEqualTo("jwt-token");
    }
}

