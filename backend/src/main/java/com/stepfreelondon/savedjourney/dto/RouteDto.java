package com.stepfreelondon.savedjourney.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The lines and stops of the journey option the user chose, taken from the journey search result.
 * Client-supplied, so it is validated for shape and size; it only affects the user's own alerts.
 */
public record RouteDto(
        @Schema(description = "Lines in travel order; empty for a walking-only journey", example = "[\"metropolitan\"]")
        @NotNull @Size(max = 20)
        List<@NotNull @Pattern(regexp = "^[a-z0-9-]{1,40}$", message = "must be a TfL line id") String> lineIds,

        @Schema(description = "Stops in travel order with their role on the route")
        @NotEmpty @Size(max = 300)
        List<@NotNull @Valid RouteStopDto> stations) {
}
