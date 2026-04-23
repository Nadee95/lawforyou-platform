package com.lawforyou.user.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.entity.*;
import com.lawforyou.user.event.EmailNotificationEvent;
import com.lawforyou.user.event.UserCreatedEvent;
import com.lawforyou.user.event.UserUpdatedEvent;
import com.lawforyou.user.mapper.UserMapper;
import com.lawforyou.user.repository.OutboxEventRepository;
import com.lawforyou.user.repository.RoleRepository;
import com.lawforyou.user.repository.UserRepository;
import com.nadeex.spring.security.properties.SecurityProperties;
import com.nadeex.spring.security.token.JwtTokenProvider;
import com.lawforyou.user.service.UserService;
import com.nadeex.spring.common.exception.ErrorCode;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.exception.ConflictException;
import com.nadeex.spring.exception.EventSerializationException;
import com.nadeex.spring.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository   userRepository;
    private final RoleRepository   roleRepository;
    private final UserMapper       userMapper;
    private final PasswordEncoder  passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties    jwtProperties;
    private final EntityManager     entityManager;
    private final ObjectMapper      objectMapper;
    private final OutboxEventRepository outboxEventRepository;


    // ── Register ─────────────────────────────────────────────────────────────

    @Override
    public UserDto register(RegisterUserRequest request, UUID tenantId)  {
        if (userRepository.existsByEmailAndTenantId(request.email(), tenantId)) {
            throw new ConflictException("User email", request.email());
        }
        if (userRepository.existsByUsernameAndTenantId(request.username(), tenantId)) {
            throw new ConflictException("User username", request.username());
        }

        Role clientRole = roleRepository
                .findByNameAndTenantIdIsNull(SystemRole.CLIENT.roleName())
                .orElseThrow(() -> new IllegalStateException(
                        "System role CLIENT not found — check Flyway seeding"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .roles(Set.of(clientRole))
                .build();

        User saved = userRepository.saveAndFlush(user);

        // Hibernate @TenantId is injected into the DB column but NOT written
        // back to the Java field. Refresh forces a SELECT that populates all
        // server-assigned fields: tenantId, createdAt, updatedAt, etc.
        entityManager.refresh(saved);

        // ── Outbox: UserCreatedEvent → user-events ────────────────────────────
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(saved.getId().toString())
                .topic("user-events")
                .eventType(UserCreatedEvent.class.getName())
                .payload(toJson(userMapper.toDto(saved)))
                .status("PENDING")
                .build());

        // ── Outbox: welcome email → notification-events ───────────────────────
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(saved.getId().toString())
                .topic(EmailNotificationEvent.TOPIC)
                .eventType(EmailNotificationEvent.class.getName())
                .payload(toJson(EmailNotificationEvent.welcome(
                        saved.getEmail(),
                        saved.getUsername(),
                        tenantId.toString(),
                        null)))
                .status("PENDING")
                .build());

        log.info("Registered user {} in tenant {}", saved.getId(), tenantId);

        return userMapper.toDto(saved);
    }

    // ── Authenticate ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AuthResult authenticate(LoginRequest request, UUID tenantId) {
        // Lookup by email or username
        User user = findByEmailOrUsername(request.usernameOrEmail(), tenantId);

        if (user == null) {
            return new AuthResult.Failure("Invalid credentials", ErrorCode.UNAUTHORIZED);
        }
        if (!user.isActive()) {
            return new AuthResult.Failure("Account is inactive", ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return new AuthResult.Failure("Invalid credentials", ErrorCode.UNAUTHORIZED);
        }

        List<String> roles       = user.getRoles().stream().map(Role::getName).toList();
        List<String> permissions = user.getRoles().stream()
                .filter(Role::isActive)
                .flatMap(r -> r.getPermissions().stream())
                .filter(Permission::isActive)
                .map(Permission::getName)
                .toList();

        String token = jwtTokenProvider.generateToken(
                user.getId(), tenantId, user.getUsername(), roles, permissions);

        return new AuthResult.Success(token, jwtProperties.getExpirationSeconds(), userMapper.toDto(user));
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserDto findById(UUID userId, UUID tenantId) {
        return userMapper.toDto(requireUser(userId, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserDto> findAll(UUID tenantId, Pageable pageable) {
        Page<User> page = userRepository.findAllByTenantId(tenantId, pageable);
        return toPagedResponse(page, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserDto> search(UUID tenantId, String query, Pageable pageable) {
        Page<User> page = userRepository.searchByTenantId(tenantId, query, pageable);
        return toPagedResponse(page, pageable);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public UserDto update(UUID userId, UUID tenantId, UpdateUserRequest request) {
        User user = requireUser(userId, tenantId);

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName()  != null) user.setLastName(request.lastName());
        if (request.phone()     != null) user.setPhone(request.phone());
        if (request.email()     != null) {
            if (!request.email().equals(user.getEmail()) &&
                    userRepository.existsByEmailAndTenantId(request.email(), tenantId)) {
                throw new ConflictException("User email", request.email());
            }
            user.setEmail(request.email());
        }

        User saved = userRepository.saveAndFlush(user);

        // Refresh forces a SELECT that populates all server-assigned field : updatedAt
        entityManager.refresh(saved);

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(saved.getId().toString())
                .topic("user-events")
                .eventType(UserUpdatedEvent.class.getName())
                .payload(toJson(userMapper.toDto(saved)))
                .status("PENDING")
                .build());

        return userMapper.toDto(saved);
    }

    @Override
    public UserDto setActive(UUID userId, UUID tenantId, boolean active) {
        User user = requireUser(userId, tenantId);
        user.setActive(active);

        User saved = userRepository.saveAndFlush(user);

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(saved.getId().toString())
                .topic("user-events")
                .eventType(UserUpdatedEvent.class.getName())
                .payload(toJson(userMapper.toDto(saved)))
                .status("PENDING")
                .build());

        return userMapper.toDto(saved);
    }

    // ── Role management ───────────────────────────────────────────────────────

    @Override
    public UserDto assignRole(UUID userId, UUID tenantId, UUID roleId) {
        User user = requireUser(userId, tenantId);
        Role role = requireRole(roleId);
        user.addRole(role);
        return userMapper.toDto(userRepository.saveAndFlush(user));
    }

    @Override
    public UserDto removeRole(UUID userId, UUID tenantId, UUID roleId) {
        User user = requireUser(userId, tenantId);
        Role role = requireRole(roleId);
        user.removeRole(role);
        return userMapper.toDto(userRepository.saveAndFlush(user));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User requireUser(UUID userId, UUID tenantId) {
        return userRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private Role requireRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));
    }

    private User findByEmailOrUsername(String usernameOrEmail, UUID tenantId) {
        boolean isEmail = usernameOrEmail.contains("@");
        if (isEmail) {
            return userRepository.findByEmailAndTenantId(usernameOrEmail, tenantId).orElse(null);
        }
        return userRepository.findByUsernameAndTenantId(usernameOrEmail, tenantId).orElse(null);
    }

    private PagedResponse<UserDto> toPagedResponse(Page<User> page, Pageable pageable) {
        List<UserDto> content = page.getContent().stream()
                .map(userMapper::toDto)
                .toList();
        return PagedResponse.of(content, pageable.getPageNumber(),
                pageable.getPageSize(), page.getTotalElements());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new EventSerializationException(
                    "Failed to serialise outbox event: " + obj.getClass().getSimpleName(), ex);
        }
    }

}

