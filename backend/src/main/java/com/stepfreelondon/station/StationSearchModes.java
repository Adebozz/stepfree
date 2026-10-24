package com.stepfreelondon.station;

import java.util.List;

/** Modes included in station search. Buses are excluded: this is a station-based planner. */
final class StationSearchModes {

    static final List<String> RAIL = List.of("tube", "overground", "elizabeth-line", "dlr", "national-rail", "tram");

    private StationSearchModes() {
    }
}
