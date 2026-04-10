package com.lawforyou.user.service.impl;

import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.entity.Permission;
import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.SystemRole;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.kafka.UserEventProducer;
import com.lawforyou.user.mapper.UserMapper;
import com.lawforyou.user.repository.RoleRepository;
import com.lawforyou.user.repository.UserRepository;
import com.lawforyou.user.security.JwtProperties;
import com.lawforyou.user.security.JwtTokenProvider;
import com.lawforyou.user.service.UserService;
import com.nadeex.spring.common.exception.ErrorCode;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.exception.ConflictException;
import com.nadeex.spring.exception.ResourceNotFoundException;
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
import java.util.stream.Collectors;

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
    private final JwtProperties    jwtProperties;
    private final UserEventProducer eventProducer;

    // ── Register ─────────────────────────────────────────────────────────────

    @Override
    public UserDto register(RegisterUserRequest request, UUID tenantId) {
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
        log.info("Registered user {} in tenant {}", saved.getId(), tenantId);

        eventProducer.publishUserCreated(saved);
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

        Set<String> roles       = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .filter(Role::isActive)
                .flatMap(r -> r.getPermissions().stream())
                .filter(Permission::isActive)
                .map(Permission::getName)
                .collect(Collectors.toSet());

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

        return userMapper.toDto(userRepository.saveAndFlush(user));
    }

    @Override
    public UserDto setActive(UUID userId, UUID tenantId, boolean active) {
        User user = requireUser(userId, tenantId);
        user.setActive(active);
        return userMapper.toDto(userRepository.saveAndFlush(user));
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
}

