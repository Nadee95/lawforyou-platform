package com.lawforyou.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.entity.OutboxEvent;
import com.lawforyou.user.event.EmailNotificationEvent;
import com.lawforyou.user.event.UserCreatedEvent;
import com.lawforyou.user.event.UserUpdatedEvent;
import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.SystemRole;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.mapper.UserMapper;
import com.lawforyou.user.repository.OutboxEventRepository;
import com.lawforyou.user.repository.RoleRepository;
import com.lawforyou.user.repository.UserRepository;
import com.nadeex.spring.exception.EventSerializationException;
import com.nadeex.spring.exception.ResourceNotFoundException;
import com.nadeex.spring.security.properties.SecurityProperties;
import com.nadeex.spring.security.token.JwtTokenProvider;
import com.lawforyou.user.keycloak.KeycloakAdminService;
import com.lawforyou.user.service.TokenBlacklistService;
import com.lawforyou.user.service.impl.UserServiceImpl;
import com.nadeex.spring.exception.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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
    @Mock SecurityProperties jwtProperties;
    @Mock EntityManager     entityManager;
    @Mock KeycloakAdminService keycloakAdminService;
    @Mock TokenBlacklistService tokenBlacklistService;


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
        when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");   // ← toJson() mock
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.register(request, TENANT_ID);

        verify(entityManager).refresh(savedUser);
        verify(userRepository).saveAndFlush(any(User.class));

        // register() saves TWO outbox events: UserCreatedEvent + welcome EmailNotificationEvent
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(captor.capture());

        List<OutboxEvent> saved = captor.getAllValues();

        OutboxEvent userCreatedEntry = saved.stream()
                .filter(e -> e.getEventType().equals(UserCreatedEvent.class.getName()))
                .findFirst().orElseThrow();
        assertThat(userCreatedEntry.getTopic()).isEqualTo("user-events");
        assertThat(userCreatedEntry.getStatus()).isEqualTo("PENDING");
        assertThat(userCreatedEntry.getAggregateId()).isEqualTo(savedUser.getId().toString());

        OutboxEvent welcomeEmailEntry = saved.stream()
                .filter(e -> e.getEventType().equals(EmailNotificationEvent.class.getName()))
                .findFirst().orElseThrow();
        assertThat(welcomeEmailEntry.getTopic()).isEqualTo("notification-events");
        assertThat(welcomeEmailEntry.getStatus()).isEqualTo("PENDING");
        assertThat(welcomeEmailEntry.getAggregateId()).isEqualTo(savedUser.getId().toString());
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

    // ── register — additional cases ───────────────────────────────────────────

    @Test
    void register_whenUsernameAlreadyExists_throwsConflict() {
        var request = new RegisterUserRequest(
                "john", "john@acme.com", "pass123", null, null, null);

        when(userRepository.existsByEmailAndTenantId(any(), any())).thenReturn(false);
        when(userRepository.existsByUsernameAndTenantId("john", TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("john");

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void register_whenClientRoleNotFound_throwsIllegalState() {
        var request = new RegisterUserRequest(
                "john", "john@acme.com", "pass123", null, null, null);

        when(userRepository.existsByEmailAndTenantId(any(), any())).thenReturn(false);
        when(userRepository.existsByUsernameAndTenantId(any(), any())).thenReturn(false);
        when(roleRepository.findByNameAndTenantIdIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.register(request, TENANT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLIENT");
    }

    @Test
    void register_whenObjectMapperFails_throwsEventSerializationException() throws JsonProcessingException {
        var request = new RegisterUserRequest(
                "john", "john@acme.com", "pass123", "John", "Doe", null);
        var clientRole = Role.builder().id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName()).systemRole(true).build();
        var savedUser = User.builder().id(UUID.randomUUID()).username("john")
                .email("john@acme.com").passwordHash("hashed").roles(Set.of(clientRole)).build();

        when(userRepository.existsByEmailAndTenantId(any(), any())).thenReturn(false);
        when(userRepository.existsByUsernameAndTenantId(any(), any())).thenReturn(false);
        when(roleRepository.findByNameAndTenantIdIsNull(any())).thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        // toDto must return non-null so writeValueAsString gets a value and the NPE in catch is avoided
        when(userMapper.toDto(savedUser)).thenReturn(mock(UserDto.class));
        when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialisation failure") {});

        assertThatThrownBy(() -> userService.register(request, TENANT_ID))
                .isInstanceOf(EventSerializationException.class);
    }

    // ── authenticate — additional cases ──────────────────────────────────────

    @Test
    void authenticate_whenUserNotFound_returnsFailure() {
        var request = new LoginRequest("ghost", "pass");

        when(userRepository.findByUsernameAndTenantId("ghost", TENANT_ID))
                .thenReturn(Optional.empty());

        AuthResult result = userService.authenticate(request, TENANT_ID);

        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void authenticate_whenUserIsInactive_returnsFailure() {
        var request = new LoginRequest("john", "pass");
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .passwordHash("hashed").active(false).roles(Set.of()).build();

        when(userRepository.findByUsernameAndTenantId("john", TENANT_ID))
                .thenReturn(Optional.of(user));

        AuthResult result = userService.authenticate(request, TENANT_ID);

        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        assertThat(((AuthResult.Failure) result).reason()).contains("inactive");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void authenticate_whenLoginWithEmail_resolvesViaEmailBranch() {
        // "john@acme.com" contains "@" → findByEmailAndTenantId must be called
        var request = new LoginRequest("john@acme.com", "correct");
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .passwordHash("hashed").active(true).roles(Set.of()).build();

        when(userRepository.findByEmailAndTenantId("john@acme.com", TENANT_ID))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any()))
                .thenReturn("jwt-token");
        when(jwtProperties.getExpirationSeconds()).thenReturn(3600L);

        AuthResult result = userService.authenticate(request, TENANT_ID);

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        verify(userRepository).findByEmailAndTenantId("john@acme.com", TENANT_ID);
        verify(userRepository, never()).findByUsernameAndTenantId(any(), any());
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_whenUserExists_returnsDto() {
        var role = Role.builder().id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName()).build();
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .email("john@acme.com").tenantId(TENANT_ID).roles(Set.of(role)).build();
        var dto = mock(UserDto.class);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto result = userService.findById(user.getId(), TENANT_ID);

        assertThat(result).isSameAs(dto);
    }

    @Test
    void findById_whenUserNotFound_throwsResourceNotFound() {
        UUID missingId = UUID.randomUUID();

        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(missingId, TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_whenUserBelongsToDifferentTenant_throwsResourceNotFound() {
        UUID otherTenant = UUID.randomUUID();
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .tenantId(otherTenant).roles(Set.of()).build(); // different tenant

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // TENANT_ID ≠ otherTenant → filter removes it → ResourceNotFoundException
        assertThatThrownBy(() -> userService.findById(user.getId(), TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .tenantId(TENANT_ID).roles(Set.of()).build();
        var page = new PageImpl<>(List.of(user), pageable, 1);

        when(userRepository.findAllByTenantId(TENANT_ID, pageable)).thenReturn(page);
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));

        var result = userService.findAll(TENANT_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void search_returnsMatchingUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .tenantId(TENANT_ID).roles(Set.of()).build();
        var page = new PageImpl<>(List.of(user), pageable, 1);

        when(userRepository.searchByTenantId(TENANT_ID, "john", pageable)).thenReturn(page);
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));

        var result = userService.search(TENANT_ID, "john", pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── update — additional cases ─────────────────────────────────────────────

    @Test
    void update_whenUserNotFound_throwsResourceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.update(missingId, TENANT_ID, new UpdateUserRequest("A", "B", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_whenEmailChangedToAlreadyUsedEmail_throwsConflict() {
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .email("old@acme.com").passwordHash("hashed")
                .active(true).roles(Set.of()).tenantId(TENANT_ID).build();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndTenantId("taken@acme.com", TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() ->
                userService.update(user.getId(), TENANT_ID,
                        new UpdateUserRequest(null, null, null, "taken@acme.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("taken@acme.com");
    }

    @Test
    void update_callsEntityManagerRefresh() throws JsonProcessingException {
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .email("john@acme.com").passwordHash("hashed")
                .active(true).roles(Set.of()).tenantId(TENANT_ID).build();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.update(user.getId(), TENANT_ID, new UpdateUserRequest("Jane", null, null, null));

        verify(entityManager).refresh(user);
    }

    // ── setActive — additional cases ──────────────────────────────────────────

    @Test
    void setActive_activatesInactiveUser() throws JsonProcessingException {
        var role = Role.builder().id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName()).systemRole(true).build();
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .email("john@acme.com").passwordHash("hashed")
                .active(false).roles(Set.of(role)).tenantId(TENANT_ID).build();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setActive(user.getId(), TENANT_ID, true);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void setActive_whenUserNotFound_throwsResourceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setActive(missingId, TENANT_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── assignRole ────────────────────────────────────────────────────────────

    @Test
    void assignRole_addsRoleToUser() {
        var existingRole = Role.builder().id(UUID.randomUUID())
                .name(SystemRole.CLIENT.roleName()).build();
        var newRole = Role.builder().id(UUID.randomUUID()).name("LAWYER").build();
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .tenantId(TENANT_ID).roles(new java.util.HashSet<>(Set.of(existingRole))).build();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findById(newRole.getId())).thenReturn(Optional.of(newRole));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        userService.assignRole(user.getId(), TENANT_ID, newRole.getId());

        assertThat(user.getRoles()).contains(newRole);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void assignRole_whenUserNotFound_throwsResourceNotFound() {
        UUID missingUser = UUID.randomUUID();
        when(userRepository.findById(missingUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.assignRole(missingUser, TENANT_ID, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignRole_whenRoleNotFound_throwsResourceNotFound() {
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .tenantId(TENANT_ID).roles(new java.util.HashSet<>()).build();
        UUID missingRole = UUID.randomUUID();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findById(missingRole)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.assignRole(user.getId(), TENANT_ID, missingRole))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── removeRole ────────────────────────────────────────────────────────────

    @Test
    void removeRole_removesRoleFromUser() {
        var role = Role.builder().id(UUID.randomUUID()).name(SystemRole.CLIENT.roleName()).build();
        var user = User.builder().id(UUID.randomUUID()).username("john")
                .tenantId(TENANT_ID).roles(new java.util.HashSet<>(Set.of(role))).build();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        userService.removeRole(user.getId(), TENANT_ID, role.getId());

        assertThat(user.getRoles()).doesNotContain(role);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void removeRole_whenUserNotFound_throwsResourceNotFound() {
        UUID missingUser = UUID.randomUUID();
        when(userRepository.findById(missingUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.removeRole(missingUser, TENANT_ID, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

