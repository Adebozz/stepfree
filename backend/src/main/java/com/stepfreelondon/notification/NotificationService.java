package com.stepfreelondon.notification;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stepfreelondon.notification.dto.AlertListDto;
import com.stepfreelondon.notification.dto.NotificationPreferencesDto;

@Service
class NotificationService {

    static final int MAX_ALERTS = 100;

    private final NotificationRepository repository;
    private final Clock clock;

    NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    AlertListDto alerts(UUID userId, boolean unreadOnly, int limit) {
        int bounded = Math.clamp(limit, 1, MAX_ALERTS);
        return new AlertListDto(repository.alerts(userId, unreadOnly, bounded), repository.unreadCount(userId));
    }

    void markRead(UUID userId, UUID alertId) {
        if (!repository.markRead(userId, alertId, clock.instant())) {
            throw new AlertNotFoundException();
        }
    }

    void markAllRead(UUID userId) {
        repository.markAllRead(userId, clock.instant());
    }

    NotificationPreferencesDto preferences(UUID userId) {
        return repository.preferences(userId);
    }

    NotificationPreferencesDto updatePreferences(UUID userId, NotificationPreferencesDto preferences) {
        repository.savePreferences(userId, preferences, clock.instant());
        return preferences;
    }
}
