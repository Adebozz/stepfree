package com.stepfreelondon.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Starts and ends the signed-in session. Login is a JSON endpoint rather than Spring Security's form
 * login filter, so the protections that filter would apply are done here explicitly.
 */
@Component
class SessionAuthenticator {

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    SessionAuthenticator(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    void signIn(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // Session fixation: an id an attacker planted before login must not become an authenticated session.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        // Since Spring Security 6 the context is not saved automatically; without this the login wouldn't stick.
        contextRepository.saveContext(context, request, response);
        // A new CSRF token after login, so a token seen before authentication is useless afterwards.
        new CsrfAuthenticationStrategy(csrfTokenRepository).onAuthentication(authentication, request, response);
    }

    /** Invalidates the session (deleting its row) and clears the security context and CSRF token. */
    void signOut(HttpServletRequest request, HttpServletResponse response) {
        new CompositeLogoutHandler(new CsrfLogoutHandler(csrfTokenRepository), new SecurityContextLogoutHandler())
                .logout(request, response, contextHolder.getContext().getAuthentication());
    }
}
