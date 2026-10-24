package com.stepfreelondon.line.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stepfreelondon.line.ServiceLevel;

public record LineStatusEntryDto(
        @Schema(description = "TfL statusSeverity code", example = "9") Integer severityCode,
        @Schema(example = "Minor Delays") String severityDescription,
        ServiceLevel level,
        @Schema(description = "TfL's explanation; null for good service") String reason,
        @Schema(description = "Start of the current validity period, if TfL supplied one") Instant validFrom,
        @Schema(description = "End of the current validity period, if TfL supplied one") Instant validTo,
        @Schema(description = "True when TfL reports 'No Step Free Access'") boolean stepFreeAccessAffected) {
}
