package com.stepfreelondon.journey.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.format.annotation.DateTimeFormat;

import com.stepfreelondon.journey.AccessibilityPreference;
import com.stepfreelondon.journey.JourneyPreference;
import com.stepfreelondon.journey.WalkingSpeed;

/**
 * Query parameters for {@code GET /api/v1/journeys}. Bound from the query string by Spring MVC's
 * constructor binding, then validated with Bean Validation.
 */
public record JourneySearchRequest(
        @Schema(description = "Origin ICS code (from station search)", example = "1000257")
        @NotBlank @Pattern(regexp = "^\\d{4,12}$", message = "must be a TfL ICS code")
        String from,

        @Schema(description = "Destination ICS code (from station search)", example = "1000129")
        @NotBlank @Pattern(regexp = "^\\d{4,12}$", message = "must be a TfL ICS code")
        String to,

        @Schema(description = "Accessibility requirements, passed to TfL unchanged. Repeat or comma-separate.")
        List<AccessibilityPreference> accessibility,

        @Schema(description = "How to rank results; TfL's default is LEAST_TIME")
        JourneyPreference preference,

        @Schema(description = "Walking pace used for walking legs and interchanges")
        WalkingSpeed walkingSpeed,

        @Schema(description = "London local date-time (no offset). Omit to leave now.", example = "2026-10-24T08:30:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime time,

        @Schema(description = "If true, 'time' is the latest arrival time instead of the departure time")
        Boolean arriveBy) {

    public JourneySearchRequest {
        accessibility = accessibility == null ? List.of() : accessibility.stream().distinct().sorted().toList();
    }

    public boolean isArriveBy() {
        return Boolean.TRUE.equals(arriveBy);
    }

    @Schema(hidden = true)
    @AssertTrue(message = "origin and destination must be different")
    public boolean isDifferentStations() {
        return from == null || !from.equals(to);
    }
}
