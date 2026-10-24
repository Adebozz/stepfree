package com.stepfreelondon.disruption.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record LiftOutageDto(
        @Schema(example = "940GZZLUWYP") String stationId,
        @Schema(example = "[\"940GZZLUWYP-Lift-5\"]") List<String> liftIds,
        @Schema(description = "TfL's description of the outage and any step-free alternative") String message) {
}
