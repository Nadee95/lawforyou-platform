package com.lawforyou.user.security;

import com.lawforyou.user.entity.Permission;
import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loads a {@link User} from the DB by userId (UUID string as username in Spring Security).
 *
 * <p>Authorities are built from the user's roles and permissions:
 * <ul>
 *   <li>Roles are prefixed with {@code ROLE_} (e.g. {@code ROLE_ADMIN})</li>
 *   <li>Permissions are added directly (e.g. {@code USER_READ})</li>
 * </ul>
 * Used by {@code DaoAuthenticationProvider} — must return a {@code UserDetails}
 * with the stored password hash so password verification can succeed.</p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with id: " + userId));

        return new org.springframework.security.core.userdetails.User(
                userId,
                user.getPasswordHash(),
                user.isActive(),
                true, true, true,
                buildAuthorities(user)
        );
    }

    private List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Role role : user.getRoles()) {
            if (!role.isActive()) continue;
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            for (Permission permission : role.getPermissions()) {
                if (permission.isActive()) {
                    authorities.add(new SimpleGrantedAuthority(permission.getName()));
                }
            }
        }
        return authorities;
    }
}
