package com.stepfreelondon.line.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stepfreelondon.line.ServiceLevel;

public record LineStatusDto(
        @Schema(example = "metropolitan") String id,
        @Schema(example = "Metropolitan") String name,
        @Schema(example = "tube") String mode,
        @Schema(description = "Most serious level across all current statuses; UNKNOWN if TfL reported none")
        ServiceLevel level,
        @Schema(description = "TfL's description of the most serious status, or 'Status unavailable'", example = "Minor Delays")
        String summary,
        @Schema(description = "Ordered most serious first")
        List<LineStatusEntryDto> statuses) {
}
