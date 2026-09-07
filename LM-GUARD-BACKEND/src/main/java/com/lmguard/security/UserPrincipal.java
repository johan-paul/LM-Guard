package com.lmguard.security;

import com.lmguard.entity.User;
import com.lmguard.entity.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated user as Spring Security sees it.
 *
 * <p>Roles are exposed as {@code ROLE_INSPECTOR} / {@code ROLE_ADMIN} so {@code hasRole(...)}
 * works as written in the controllers.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;

    public UserPrincipal(UUID id, String name, String email, String passwordHash, Role role, boolean enabled) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security's "username" is the login email for this application. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
