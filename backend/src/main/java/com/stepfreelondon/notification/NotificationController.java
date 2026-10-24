package com.stepfreelondon.notification;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.auth.AuthUser;
import com.stepfreelondon.notification.dto.AlertListDto;
import com.stepfreelondon.notification.dto.NotificationPreferencesDto;

@Tag(name = "Alerts and notifications")
@RestController
@RequestMapping("/api/v1")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "The signed-in user's journey alerts, newest first, with the unread count")
    @GetMapping("/alerts")
    AlertListDto alerts(@AuthenticationPrincipal AuthUser user,
                        @RequestParam(defaultValue = "false") boolean unreadOnly,
                        @RequestParam(defaultValue = "50") int limit) {
        return service.alerts(user.id(), unreadOnly, limit);
    }

    @Operation(summary = "Mark one alert as read")
    @PostMapping("/alerts/{id}/read")
    ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        service.markRead(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark every alert as read")
    @PostMapping("/alerts/read-all")
    ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthUser user) {
        service.markAllRead(user.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Notification preferences (defaults: email off, monitoring on)")
    @GetMapping("/notification-preferences")
    NotificationPreferencesDto preferences(@AuthenticationPrincipal AuthUser user) {
        return service.preferences(user.id());
    }

    @Operation(summary = "Replace notification preferences")
    @PutMapping("/notification-preferences")
    NotificationPreferencesDto updatePreferences(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody NotificationPreferencesDto body) {
        return service.updatePreferences(user.id(), body);
    }
}
