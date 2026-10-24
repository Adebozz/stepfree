package com.stepfreelondon.auth;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in principal stored in the session. Holds only the user id: names and emails are
 * read from the database on each request, so a profile change is never stale in another session.
 *
 * <p>{@link #getUsername()} returns the id, not the email. Spring Session indexes sessions by this
 * value, so we can find (and delete) every session of a user, and an email change doesn't orphan them.
 * The password hash is needed only during login and is erased afterwards ({@link CredentialsContainer}),
 * so it is never serialised into the session table.
 */
public final class AuthUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final UUID id;
    private final boolean enabled;
    private String passwordHash;

    AuthUser(UUID id, String passwordHash, boolean enabled) {
        this.id = id;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public UUID id() {
        return id;
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
