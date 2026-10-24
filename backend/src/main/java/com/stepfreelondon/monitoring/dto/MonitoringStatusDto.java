package com.stepfreelondon.monitoring.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record MonitoringStatusDto(
        @Schema(description = "False if this server's scheduler is off; alerts then only appear when a cycle is triggered externally")
        boolean schedulerEnabled,
        @Schema(description = "When the last completed cycle finished; null if none has run") Instant lastCheckedAt,
        @Schema(description = "SUCCEEDED, PARTIAL (some TfL feeds unavailable) or FAILED; null if none has run") String lastStatus) {
}
