package com.stepfreelondon.auth;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Finds a user's sessions through Spring Session's principal-name index (the user id, see {@link AuthUser}). */
@Component
class UserSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    UserSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * Signs the user out everywhere except the current session. The current one is left to
     * {@code HttpSession.invalidate()}, because deleting it underneath the request would let Spring
     * Session try to save it again when the request ends.
     */
    void deleteAllExcept(UUID userId, String currentSessionId) {
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(currentSessionId))
                .forEach(sessions::deleteById);
    }
}
