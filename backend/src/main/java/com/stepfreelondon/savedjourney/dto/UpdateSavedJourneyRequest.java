package com.stepfreelondon.savedjourney.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Omitted fields are left unchanged. The route can't be edited; save a new journey instead. */
public record UpdateSavedJourneyRequest(
        @Size(min = 1, max = 80) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        Boolean monitoringEnabled) {
}
