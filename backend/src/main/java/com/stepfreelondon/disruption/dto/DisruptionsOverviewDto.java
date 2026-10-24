package com.stepfreelondon.disruption.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Network-wide station disruptions and lift outages. Line-level disruptions (with reasons) come
 * from {@code /api/v1/lines/status}, because TfL's line disruption feed carries no line ids.
 */
public record DisruptionsOverviewDto(
        List<StationDisruptionDto> stationDisruptions,
        List<LiftOutageDto> liftOutages,
        @Schema(description = "False if TfL's lift outage feed could not be read; liftOutages is then empty and must "
                + "not be read as 'all lifts working'")
        boolean liftDataAvailable) {
}
