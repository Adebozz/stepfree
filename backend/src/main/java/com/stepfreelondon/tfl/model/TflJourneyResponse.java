package com.stepfreelondon.tfl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Raw response of {@code GET /Journey/JourneyResults/{from}/to/{to}}. Times are zone-less London
 * local times and are kept as strings; see {@link com.stepfreelondon.tfl.TflDateTimes}.
 *
 * <p>{@code lines} holds the current status of every line used by the returned journeys.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflJourneyResponse(List<Journey> journeys, List<TflLine> lines, List<String> stopMessages) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Journey(String startDateTime, String arrivalDateTime, Integer duration, List<Leg> legs, Fare fare) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fare(Integer totalCost) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(Integer duration, Instruction instruction, List<Obstacle> obstacles,
                      String departureTime, String arrivalTime, Point departurePoint, Point arrivalPoint,
                      Path path, List<RouteOption> routeOptions, Identifier mode, List<Disruption> disruptions,
                      Boolean isDisrupted, Double distance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Instruction(String summary, String detailed, List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String descriptionHeading, String description) {
    }

    /** A physical feature on the route, e.g. {@code {type: LIFT, incline: UP, position: IDEST}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Obstacle(String type, String incline, Integer stopId, String position) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(String naptanId, String commonName, Double lat, Double lon, String icsCode) {
    }

    /** {@code lineString} is a JSON-encoded string of {@code [lat, lon]} pairs, not a JSON array. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Path(String lineString, List<Identifier> stopPoints) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteOption(String name, List<String> directions, Identifier lineIdentifier) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identifier(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Disruption(String category, String type, String description, String lastUpdate) {
    }
}
