package com.stepfreelondon.auth;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stepfreelondon.auth.dto.ChangePasswordRequest;
import com.stepfreelondon.auth.dto.RegisterRequest;
import com.stepfreelondon.auth.dto.UpdateProfileRequest;
import com.stepfreelondon.auth.dto.UserDto;

/**
 * Account rules: registration, credential checks, profile changes and deletion. Knows nothing about
 * HTTP sessions; {@link SessionAuthenticator} and {@link UserSessions} handle those.
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;
    private final Clock clock;

    AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                LoginRateLimiter rateLimiter, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        Passwords.requireWithinBcryptLimit("password", request.password());
        String email = Emails.normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        AppUser user = new AppUser(request.fullName().strip(), email, passwordEncoder.encode(request.password()), clock.instant());
        try {
            return toDto(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same email raced past existsByEmail; the UNIQUE constraint caught it.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /** An authenticated token for a user who just registered, so they don't have to sign in again. */
    Authentication authenticationFor(UUID userId) {
        AuthUser principal = new AuthUser(userId, null, true);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    /**
     * Checks email + password, subject to the login rate limit.
     *
     * @throws TooManyLoginAttemptsException if this email or IP has too many recent failures
     * @throws InvalidCredentialsException for an unknown email, a wrong password or a disabled account alike
     */
    public Authentication authenticate(String rawEmail, String password, String clientIp) {
        String email = Emails.normalise(rawEmail);
        rateLimiter.checkAllowed(email, clientIp);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            rateLimiter.recordSuccess(email);
            return authentication;
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(email, clientIp);
            throw new InvalidCredentialsException();
        }
    }

    @Transactional(readOnly = true)
    public UserDto currentUser(UUID userId) {
        return toDto(load(userId));
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request, String clientIp) {
        AppUser user = load(userId);
        if (request.fullName() != null) {
            user.rename(request.fullName().strip(), clock.instant());
        }
        if (request.email() != null) {
            String email = Emails.normalise(request.email());
            if (!email.equals(user.getEmail())) {
                requireCurrentPassword(user, request.currentPassword(), clientIp);
                if (users.existsByEmail(email)) {
                    throw new EmailAlreadyRegisteredException();
                }
                user.changeEmail(email, clock.instant());
            }
        }
        try {
            return toDto(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, String clientIp) {
        Passwords.requireWithinBcryptLimit("newPassword", request.newPassword());
        AppUser user = load(userId);
        requireCurrentPassword(user, request.currentPassword(), clientIp);
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()), clock.instant());
    }

    /** Hard delete. Saved journeys (and in Phase 4, alerts) go with it via ON DELETE CASCADE. */
    @Transactional
    public void deleteAccount(UUID userId, String password, String clientIp) {
        AppUser user = load(userId);
        requireCurrentPassword(user, password, clientIp);
        users.delete(user);
    }

    /**
     * Re-confirms the password for sensitive changes. Shares the login rate limit, so a stolen session
     * can't be used to brute-force the password either.
     */
    private void requireCurrentPassword(AppUser user, String password, String clientIp) {
        rateLimiter.checkAllowed(user.getEmail(), clientIp);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.recordFailure(user.getEmail(), clientIp);
            throw new WrongPasswordException();
        }
    }

    private AppUser load(UUID userId) {
        return users.findById(userId).orElseThrow(AccountNotFoundException::new);
    }

    private static UserDto toDto(AppUser user) {
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }
}
