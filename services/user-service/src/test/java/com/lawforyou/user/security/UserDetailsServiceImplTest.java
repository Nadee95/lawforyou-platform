package com.lawforyou.user.security;

import com.lawforyou.user.entity.Permission;
import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserDetailsServiceImpl}.
 * Tests authority-building logic (roles + permissions, active flags).
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserDetailsServiceImpl service;

    private static final UUID USER_ID = UUID.randomUUID();

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Permission activePermission(String name) {
        return Permission.builder().id(UUID.randomUUID())
                .name(name).resource("RESOURCE").action("ACTION").active(true).build();
    }

    private Permission inactivePermission(String name) {
        return Permission.builder().id(UUID.randomUUID())
                .name(name).resource("RESOURCE").action("ACTION").active(false).build();
    }

    private Role activeRole(String name, Permission... permissions) {
        Role role = Role.builder().id(UUID.randomUUID())
                .name(name).active(true).build();
        role.setPermissions(Set.of(permissions));
        return role;
    }

    private Role inactiveRole(String name) {
        return Role.builder().id(UUID.randomUUID()).name(name).active(false).build();
    }

    private User userWithRoles(Set<Role> roles) {
        User u = User.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$10$hash")
                .active(true)
                .build();
        u.setRoles(roles);
        return u;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_userNotFound_throwsUsernameNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(USER_ID.toString()))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());
    }

    @Test
    void loadUserByUsername_activeUserWithOneRole_buildsRoleAndPermissionAuthorities() {
        Role lawyerRole = activeRole("LAWYER",
                activePermission("CASE_READ"),
                activePermission("CASE_CREATE"));
        User user = userWithRoles(Set.of(lawyerRole));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(USER_ID.toString());

        assertThat(details.getUsername()).isEqualTo(USER_ID.toString());
        assertThat(details.isEnabled()).isTrue();

        var authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).contains("ROLE_LAWYER", "CASE_READ", "CASE_CREATE");
    }

    @Test
    void loadUserByUsername_inactiveRole_isSkippedEntirely() {
        Role inactiveRole = inactiveRole("ADMIN");
        User user = userWithRoles(Set.of(inactiveRole));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(USER_ID.toString());

        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    void loadUserByUsername_activeRoleWithInactivePermission_permissionExcluded() {
        Role role = activeRole("CLIENT",
                activePermission("CASE_READ"),
                inactivePermission("CASE_DELETE")); // inactive — must be filtered out
        User user = userWithRoles(Set.of(role));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(USER_ID.toString());

        var authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).contains("ROLE_CLIENT", "CASE_READ");
        assertThat(authorities).doesNotContain("CASE_DELETE");
    }

    @Test
    void loadUserByUsername_noRoles_returnsEmptyAuthorities() {
        User user = userWithRoles(Set.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(USER_ID.toString());

        assertThat(details.getAuthorities()).isEmpty();
    }
}

