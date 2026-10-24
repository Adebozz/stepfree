package com.stepfreelondon.tfl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Raw response of {@code GET /StopPoint/{id}}. For a hub, {@code children} contains the member
 * stations (each with its own properties); for a station, it contains platforms and entrances.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflStopPoint(String naptanId, String commonName, String icsCode, String stopType, List<String> modes,
                           Double lat, Double lon, List<Line> lines, List<Property> additionalProperties,
                           List<TflStopPoint> children) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String id, String name) {
    }

    /** E.g. {@code {category: "Accessibility", key: "AccessViaLift", value: "Yes"}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Property(String category, String key, String value) {
    }
}
