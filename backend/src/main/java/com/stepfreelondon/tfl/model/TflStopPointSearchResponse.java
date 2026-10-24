package com.stepfreelondon.tfl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw response of {@code GET /StopPoint/Search/{query}}. Only the fields we use are mapped. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflStopPointSearchResponse(String query, Integer total, List<Match> matches) {

    /**
     * One search hit. {@code id} is a NaPTAN id (e.g. {@code 940GZZLUWYP}) or a hub id
     * (e.g. {@code HUBWMB}); {@code icsId} is what the journey planner accepts for both.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String id, String icsId, String name, List<String> modes, String zone, Double lat, Double lon) {
    }
}
