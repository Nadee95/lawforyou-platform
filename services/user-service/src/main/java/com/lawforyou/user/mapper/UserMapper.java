package com.lawforyou.user.mapper;

import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.entity.Permission;
import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * MapStruct mapper converting {@link User} entities to read models.
 *
 * <p>Role names and permission names are flattened into {@code Set<String>}
 * so the DTO carries all access-control data a caller needs.</p>
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles",       source = "roles", qualifiedByName = "rolesToNames")
    @Mapping(target = "permissions", source = "roles", qualifiedByName = "rolesToPermissions")
    UserDto toDto(User user);

    // ── Custom mappings ────────────────────────────────────────────────────

    @Named("rolesToNames")
    static Set<String> rolesToNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Named("rolesToPermissions")
    static Set<String> rolesToPermissions(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .filter(Role::isActive)
                .flatMap(r -> r.getPermissions().stream())
                .filter(Permission::isActive)
                .map(Permission::getName)
                .collect(Collectors.toUnmodifiableSet());
    }
}

