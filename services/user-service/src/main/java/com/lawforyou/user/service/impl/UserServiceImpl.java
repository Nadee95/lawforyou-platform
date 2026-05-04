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
import com.lawforyou.user.keycloak.KeycloakAdminService;
import com.lawforyou.user.keycloak.KeycloakTokenResponse;
import com.lawforyou.user.service.TokenBlacklistService;
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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final UserMapper            userMapper;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final SecurityProperties    jwtProperties;
    private final EntityManager         entityManager;
    private final ObjectMapper          objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final KeycloakAdminService  keycloakAdminService;


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

        // ── Keycloak: create identity (Phase 4+) ─────────────────────────────
        // Best-effort — if Keycloak is disabled or unreachable, registration
        // succeeds with legacy HS256 auth until the KC identity is backfilled.
        keycloakAdminService.createUser(saved, tenantId, request.password())
                .ifPresent(keycloakId -> {
                    saved.setKeycloakId(keycloakId);
                    userRepository.save(saved);
                    log.debug("Keycloak identity created for user {}", saved.getId());
                });

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
        // Validate credentials against PostgreSQL first (prevents enumeration attacks)
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

        // ── Phase 4: Keycloak token (RS256) ──────────────────────────────────
        // If the user has a Keycloak identity, proxy the login to Keycloak and
        // return a real Keycloak RS256 token. The API Gateway will validate it
        // via JWKS in DualTokenAuthenticationFilter.
        if (user.getKeycloakId() != null) {
            Optional<KeycloakTokenResponse> kcToken =
                    keycloakAdminService.loginForToken(request.usernameOrEmail(), request.password());
            if (kcToken.isPresent()) {
                KeycloakTokenResponse kc = kcToken.get();
                log.debug("Keycloak login ok for user {}", user.getId());
                return new AuthResult.Success(kc.accessToken(), kc.refreshToken(),
                        kc.expiresIn(), userMapper.toDto(user));
            }
            log.warn("Keycloak login failed for user {} — falling back to legacy HS256", user.getId());
        }

        // ── Legacy fallback: HS256 token ─────────────────────────────────────
        List<String> roles       = user.getRoles().stream().map(Role::getName).toList();
        List<String> permissions = user.getRoles().stream()
                .filter(Role::isActive)
                .flatMap(r -> r.getPermissions().stream())
                .filter(Permission::isActive)
                .map(Permission::getName)
                .toList();

        String token = jwtTokenProvider.generateToken(
                user.getId(), tenantId, user.getUsername(), roles, permissions);

        return new AuthResult.Success(token, null, jwtProperties.getExpirationSeconds(),
                userMapper.toDto(user));
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

    // ── Logout ────────────────────────────────────────────────────────────────

    @Override
    public void logout(String token) {
        // Calculate actual remaining TTL from the JWT exp claim so we don't
        // over-blacklist (short-lived KC tokens) or under-blacklist (legacy tokens).
        long ttlMs = getRemainingTtlMs(token);

        tokenBlacklistService.blacklist(token, ttlMs);

        // If this is a Keycloak token, also revoke the user's Keycloak sessions
        // so refresh tokens are invalidated server-side.
        if ("RS256".equals(readJwtAlgorithm(token))) {
            String userId = readJwtClaim(token, "user_id");
            if (userId != null) {
                try {
                    keycloakAdminService.revokeUserSessions(UUID.fromString(userId));
                } catch (Exception e) {
                    log.warn("Could not revoke Keycloak sessions on logout: {}", e.getMessage());
                }
            }
        }

        log.info("User logged out — token blacklisted (ttl={}ms)", ttlMs);
    }

    /** Reads the remaining lifetime (ms) from the JWT exp claim without signature verification. */
    private long getRemainingTtlMs(String token) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            Object exp = claims.get("exp");
            if (exp instanceof Number expNum) {
                long remainingMs = (expNum.longValue() * 1000L) - System.currentTimeMillis();
                return Math.max(remainingMs, 60_000L); // at least 1 min
            }
        } catch (Exception e) {
            log.debug("Could not parse JWT exp claim: {}", e.getMessage());
        }
        return jwtProperties.getExpirationMs(); // fallback
    }

    /** Reads the alg field from the JWT header without signature verification. */
    private String readJwtAlgorithm(String token) {
        return readJwtHeader(token, "alg");
    }

    /** Reads a claim from the JWT payload without signature verification. */
    private String readJwtClaim(String token, String claimName) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            Object val = claims.get(claimName);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String readJwtHeader(String token, String fieldName) {
        try {
            String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
            @SuppressWarnings("unchecked")
            Map<String, Object> h = objectMapper.readValue(header, Map.class);
            Object val = h.get(fieldName);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

}

