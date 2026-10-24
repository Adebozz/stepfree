package com.stepfreelondon.accessibility.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;

/**
 * Everything TfL publishes about a station's accessibility. A hub (e.g. Wembley Central) contains one
 * section per member station, because TfL records accessibility per station, not per hub.
 */
public record StationAccessibilityDto(
        String stationId,
        String name,
        boolean hub,
        List<SectionDto> stations,
        List<LiftOutageDto> liftOutages,
        @Schema(description = "False if lift outage data could not be retrieved") boolean liftDataAvailable,
        List<StationDisruptionDto> disruptions,
        @Schema(description = "False if station disruption data could not be retrieved") boolean disruptionDataAvailable,
        @Schema(description = "When the station's static accessibility/facility data was retrieved from TfL")
        Instant stationInfoRetrievedAt) {

    public record SectionDto(
            String id,
            String name,
            List<String> modes,
            @Schema(description = "Known accessibility fields. value is null when TfL publishes nothing for it.")
            List<FactDto> accessibility,
            @Schema(description = "TfL's free-text accessibility notes, sometimes truncated by TfL") String additionalInformation,
            List<FactDto> facilities) {
    }

    public record FactDto(
            @Schema(example = "AccessViaLift") String key,
            @Schema(example = "Step-free access via lift") String label,
            @Schema(description = "TfL's value, e.g. 'Yes', 'No', '5'; null means information unavailable", example = "Yes")
            String value) {
    }
}
