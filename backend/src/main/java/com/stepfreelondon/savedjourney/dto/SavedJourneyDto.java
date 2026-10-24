package com.stepfreelondon.savedjourney.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stepfreelondon.journey.AccessibilityPreference;
import com.stepfreelondon.journey.JourneyPreference;

public record SavedJourneyDto(
        UUID id,
        String name,
        StationRefDto origin,
        StationRefDto destination,
        List<AccessibilityPreference> accessibility,
        JourneyPreference preference,
        @Schema(description = "Whether Phase 4 monitoring should watch this journey") boolean monitoringEnabled,
        List<String> lineIds,
        List<RouteStopDto> stations,
        Instant createdAt,
        Instant updatedAt) {

    public record StationRefDto(
            @Schema(example = "940GZZLUWYP") String id,
            @Schema(description = "Needed to re-plan the journey; null if TfL has none", example = "1000257") String icsCode,
            @Schema(example = "Wembley Park") String name) {
    }
}
