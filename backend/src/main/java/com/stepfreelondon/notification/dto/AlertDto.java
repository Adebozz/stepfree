package com.stepfreelondon.notification.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record AlertDto(
        UUID id,
        UUID journeyId,
        @Schema(example = "Home to work") String journeyName,
        @Schema(description = "NEW, UPDATED or RESOLVED", example = "NEW") String type,
        @Schema(example = "Metropolitan: Minor Delays") String title,
        @Schema(description = "TfL's text at the time of the alert") String message,
        Instant createdAt,
        boolean read) {
}
