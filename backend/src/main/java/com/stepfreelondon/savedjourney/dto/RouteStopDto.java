package com.stepfreelondon.savedjourney.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.stepfreelondon.savedjourney.RouteStationRole;

public record RouteStopDto(
        @Schema(example = "940GZZLUWYP") @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "must be a TfL stop id") String id,
        @NotNull RouteStationRole role) {
}
