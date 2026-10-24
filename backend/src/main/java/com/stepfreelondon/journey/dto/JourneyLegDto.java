package com.stepfreelondon.journey.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record JourneyLegDto(
        @Schema(example = "tube") String mode,
        @Schema(description = "Line or bus route id; null for walking", example = "metropolitan") String lineId,
        @Schema(example = "Metropolitan") String lineName,
        @Schema(example = "Metropolitan line to King's Cross St. Pancras") String summary,
        @Schema(example = "Metropolitan line towards Aldgate") String detail,
        Instant departureTime,
        Instant arrivalTime,
        int durationMinutes,
        Double distanceMetres,
        PlaceDto from,
        PlaceDto to,
        @Schema(description = "Stops passed through, excluding the boarding and alighting stops") List<PlaceDto> intermediateStops,
        @Schema(description = "Route geometry as [lat, lon] pairs from TfL; empty when TfL gave none. Never synthesised.")
        List<List<Double>> path,
        @Schema(description = "Turn-by-turn steps for walking legs") List<StepDto> steps,
        @Schema(description = "Lifts, stairs, escalators and walkways TfL reports on this leg") List<ObstacleDto> obstacles,
        List<DisruptionDto> disruptions,
        boolean disrupted) {

    public record PlaceDto(
            @Schema(description = "TfL NaPTAN id if this is a stop; null for addresses") String id,
            String name,
            Double latitude,
            Double longitude) {
    }

    public record StepDto(String heading, String description) {
    }

    public record ObstacleDto(
            @Schema(example = "LIFT") String type,
            @Schema(example = "UP") String incline,
            @Schema(description = "TfL's position code, e.g. IDEST (at the destination stop of the leg)") String position) {
    }

    public record DisruptionDto(String category, String description, Instant lastUpdated) {
    }
}
