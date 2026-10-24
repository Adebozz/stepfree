package com.stepfreelondon.journey.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record JourneyOptionDto(
        Instant departureTime,
        Instant arrivalTime,
        int durationMinutes,
        @Schema(description = "Number of changes between vehicles (walking legs excluded)") int interchanges,
        @Schema(description = "Total minutes spent on walking legs") int walkingMinutes,
        @Schema(description = "Distinct modes in travel order", example = "[\"walking\", \"tube\"]") List<String> modes,
        @Schema(description = "Lines ridden, in travel order", example = "[\"metropolitan\"]") List<String> lineIds,
        @Schema(description = "True if TfL flagged any leg as disrupted") boolean disrupted,
        @Schema(description = "Adult pay-as-you-go fare in pence, when TfL supplies one") Integer fareTotalPence,
        @Schema(description = "Counts of obstacles TfL reports on this journey") ObstacleSummaryDto obstacles,
        List<JourneyLegDto> legs) {

    /**
     * Counts of obstacle types TfL reported. These come only from TfL's {@code obstacles} data; zero
     * means "none reported", not "guaranteed absent".
     */
    public record ObstacleSummaryDto(int lifts, int stairs, int escalators, int walkways) {
    }
}
