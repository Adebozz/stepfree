package com.stepfreelondon.station.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A station search result, decoupled from TfL's response shape. */
public record StationSummaryDto(
        @Schema(description = "Stable TfL identifier: a NaPTAN id (940GZZLUWYP) or hub id (HUBWMB)", example = "940GZZLUWYP")
        String id,
        @Schema(description = "TfL ICS code, used for journey planning. Null if TfL did not supply one.", example = "1000257")
        String icsCode,
        @Schema(example = "Wembley Park")
        String name,
        @Schema(description = "Rail modes serving the station", example = "[\"tube\"]")
        List<String> modes,
        @Schema(example = "4")
        String zone,
        Double latitude,
        Double longitude,
        @Schema(description = "True if this is an interchange grouping several stations (e.g. Tube + National Rail)")
        boolean hub) {
}
