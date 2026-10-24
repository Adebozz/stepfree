package com.stepfreelondon.savedjourney.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.stepfreelondon.journey.AccessibilityPreference;
import com.stepfreelondon.journey.JourneyPreference;

public record CreateSavedJourneyRequest(
        @Schema(example = "Home to work") @NotBlank @Size(max = 80) String name,

        @Schema(description = "TfL NaPTAN or hub id of the origin", example = "940GZZLUWYP")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "must be a TfL station id") String originId,

        @Schema(description = "TfL NaPTAN or hub id of the destination", example = "HUBKGX")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "must be a TfL station id") String destinationId,

        List<@NotNull AccessibilityPreference> accessibility,

        JourneyPreference preference,

        @NotNull @Valid RouteDto route) {

    public CreateSavedJourneyRequest {
        accessibility = accessibility == null ? List.of() : accessibility.stream().distinct().sorted().toList();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "origin and destination must be different")
    public boolean isDifferentStations() {
        return originId == null || !originId.equals(destinationId);
    }
}
