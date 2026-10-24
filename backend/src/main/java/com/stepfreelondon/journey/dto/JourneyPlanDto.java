package com.stepfreelondon.journey.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stepfreelondon.journey.AccessibilityPreference;
import com.stepfreelondon.line.dto.LineStatusDto;

public record JourneyPlanDto(
        @Schema(description = "Journey options in TfL's order. Empty if TfL returned none.")
        List<JourneyOptionDto> journeys,
        @Schema(description = "Current status of every line used by these journeys")
        List<LineStatusDto> lineStatuses,
        @Schema(description = "The accessibility preferences TfL was asked to honour")
        List<AccessibilityPreference> appliedAccessibility,
        @Schema(description = "Free-text notices TfL attached to the stops searched")
        List<String> stopMessages) {
}
