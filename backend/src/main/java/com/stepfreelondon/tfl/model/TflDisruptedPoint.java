package com.stepfreelondon.tfl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw station disruption from {@code /StopPoint/{ids}/Disruption} or {@code /StopPoint/Mode/{modes}/Disruption}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflDisruptedPoint(String atcoCode, String stationAtcoCode, String commonName, String description,
                                String type, String appearance, String fromDate, String toDate, String mode,
                                List<ConcernedLine> concernedLines) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConcernedLine(String id) {
    }
}
