package com.stepfreelondon.accessibility.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record StationDetailDto(
        @Schema(example = "940GZZLUWYP") String id,
        @Schema(example = "Wembley Park") String name,
        @Schema(example = "1000257") String icsCode,
        List<String> modes,
        List<LineRefDto> lines,
        Double latitude,
        Double longitude,
        @Schema(description = "True for an interchange grouping several stations") boolean hub) {

    public record LineRefDto(@Schema(example = "jubilee") String id, @Schema(example = "Jubilee") String name) {
    }
}
