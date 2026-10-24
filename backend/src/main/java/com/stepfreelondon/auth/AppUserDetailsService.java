package com.stepfreelondon.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looks users up by email for Spring Security's {@code DaoAuthenticationProvider}. */
@Service
class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(Emails.normalise(email))
                .map(user -> new AuthUser(user.getId(), user.getPasswordHash(), user.isEnabled()))
                // The message never reaches the client; the provider turns this into BadCredentialsException.
                .orElseThrow(() -> new UsernameNotFoundException("unknown email"));
    }
}
