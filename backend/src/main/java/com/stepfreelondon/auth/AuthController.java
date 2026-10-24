package com.stepfreelondon.auth;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.auth.dto.ChangePasswordRequest;
import com.stepfreelondon.auth.dto.DeleteAccountRequest;
import com.stepfreelondon.auth.dto.LoginRequest;
import com.stepfreelondon.auth.dto.RegisterRequest;
import com.stepfreelondon.auth.dto.UpdateProfileRequest;
import com.stepfreelondon.auth.dto.UserDto;

/**
 * Session-cookie authentication for the SPA. Every POST/PATCH/DELETE, including login, needs the
 * CSRF token: read the {@code XSRF-TOKEN} cookie and send it as the {@code X-XSRF-TOKEN} header.
 */
@Tag(name = "Account")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator sessionAuthenticator;
    private final UserSessions userSessions;

    AuthController(AuthService authService, SessionAuthenticator sessionAuthenticator, UserSessions userSessions) {
        this.authService = authService;
        this.sessionAuthenticator = sessionAuthenticator;
        this.userSessions = userSessions;
    }

    @Operation(summary = "Issue the CSRF cookie",
            description = "Sets the XSRF-TOKEN cookie. Call once on start-up and again after signing in or out.")
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(@Parameter(hidden = true) CsrfToken token) {
        // The token is generated lazily; reading it makes Spring Security write the cookie.
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create an account and sign in")
    @PostMapping("/register")
    ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
                                     HttpServletResponse response) {
        UserDto user = authService.register(body);
        sessionAuthenticator.signIn(authService.authenticationFor(user.id()), request, response);
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(user);
    }

    @Operation(summary = "Sign in", description = "Wrong email and wrong password get the same response. "
            + "Repeated failures are refused with 429 and Retry-After.")
    @PostMapping("/login")
    UserDto login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authService.authenticate(body.email(), body.password(), request.getRemoteAddr());
        sessionAuthenticator.signIn(authentication, request, response);
        return authService.currentUser(((AuthUser) authentication.getPrincipal()).id());
    }

    @Operation(summary = "Sign out")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticator.signOut(request, response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "The signed-in user")
    @GetMapping("/me")
    UserDto me(@AuthenticationPrincipal AuthUser user) {
        return authService.currentUser(user.id());
    }

    @Operation(summary = "Update name or email", description = "Changing the email requires currentPassword.")
    @PatchMapping("/me")
    UserDto updateProfile(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UpdateProfileRequest body,
                          HttpServletRequest request) {
        return authService.updateProfile(user.id(), body, request.getRemoteAddr());
    }

    @Operation(summary = "Change password", description = "Signs out every other session.")
    @PostMapping("/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangePasswordRequest body,
                                        HttpServletRequest request) {
        authService.changePassword(user.id(), body, request.getRemoteAddr());
        userSessions.deleteAllExcept(user.id(), request.getSession().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete account", description = "Permanently deletes the account and saved journeys, "
            + "and signs out every session.")
    @DeleteMapping("/me")
    ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody DeleteAccountRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        authService.deleteAccount(user.id(), body.password(), request.getRemoteAddr());
        userSessions.deleteAllExcept(user.id(), request.getSession().getId());
        sessionAuthenticator.signOut(request, response);
        return ResponseEntity.noContent().build();
    }
}
