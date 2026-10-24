package com.stepfreelondon.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * @param emailEnabled      send an email for each alert (off until the user turns it on)
 * @param monitoringEnabled check this user's saved journeys at all; each journey also has its own switch
 */
public record NotificationPreferencesDto(
        @NotNull @Schema(example = "false") Boolean emailEnabled,
        @NotNull @Schema(example = "true") Boolean monitoringEnabled) {

    public static final NotificationPreferencesDto DEFAULTS = new NotificationPreferencesDto(false, true);
}
