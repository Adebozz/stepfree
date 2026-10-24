package com.stepfreelondon.tfl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw line entry from {@code GET /Line/Mode/{modes}/Status}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflLine(String id, String name, String modeName, List<LineStatus> lineStatuses) {

    /**
     * Dates are kept as strings: TfL mixes UTC ({@code ...Z}) and zone-less timestamps, and one
     * unparseable date must not break the whole status feed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineStatus(Integer statusSeverity, String statusSeverityDescription, String reason,
                             List<ValidityPeriod> validityPeriods) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidityPeriod(String fromDate, String toDate, Boolean isNow) {
    }
}
