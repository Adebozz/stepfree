package com.stepfreelondon.monitoring;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A saved journey with monitoring on (for the journey and its owner), with the route it was saved with.
 *
 * @param stationRoles NaPTAN/hub id → ORIGIN, INTERCHANGE, DESTINATION or INTERMEDIATE
 */
record MonitoredJourney(UUID id, UUID userId, String name, Set<String> lineIds, Map<String, String> stationRoles) {

    static final String INTERMEDIATE = "INTERMEDIATE";

    MonitoredJourney {
        lineIds = Set.copyOf(lineIds);
        stationRoles = Map.copyOf(stationRoles);
    }

    /** Stations where the passenger enters, leaves or changes, i.e. uses lifts and walkways. */
    boolean usesStation(String stationId) {
        String role = stationRoles.get(stationId);
        return role != null && !INTERMEDIATE.equals(role);
    }

    boolean passesStation(String stationId) {
        return stationRoles.containsKey(stationId);
    }
}
