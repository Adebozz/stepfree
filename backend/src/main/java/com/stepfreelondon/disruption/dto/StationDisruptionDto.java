package com.stepfreelondon.disruption.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record StationDisruptionDto(
        @Schema(example = "940GZZLUWYP") String stationId,
        @Schema(example = "Wembley Park") String stationName,
        @Schema(description = "TfL disruption type", example = "Part Closure") String type,
        @Schema(description = "TfL appearance, e.g. RealTime or PlannedWork", example = "PlannedWork") String appearance,
        String description,
        Instant validFrom,
        Instant validTo,
        @Schema(description = "Lines TfL says are concerned") List<String> lineIds,
        @Schema(description = "True if the description mentions lifts, escalators or step-free access. "
                + "A keyword match, not a TfL classification.")
        boolean mentionsAccessibility) {
}
