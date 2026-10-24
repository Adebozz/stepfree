package com.stepfreelondon.tfl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Raw entry from {@code GET /Disruptions/Lifts/v2/}. This endpoint is not in TfL's published swagger
 * (see docs/feasibility.md §1.7), so callers must tolerate it failing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflLiftDisruption(String stationUniqueId, List<String> disruptedLiftUniqueIds, String message) {
}
