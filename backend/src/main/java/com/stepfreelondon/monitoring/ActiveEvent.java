package com.stepfreelondon.monitoring;

import java.util.Set;
import java.util.UUID;

/** An ACTIVE {@code disruption_event} row with its scope, as loaded at the start of a cycle. */
record ActiveEvent(UUID id, DisruptionSource source, String identityKey, String contentHash, int revision,
                   String title, String description, Set<String> lineIds, Set<String> stationIds) {

    ActiveEvent {
        lineIds = Set.copyOf(lineIds);
        stationIds = Set.copyOf(stationIds);
    }
}
