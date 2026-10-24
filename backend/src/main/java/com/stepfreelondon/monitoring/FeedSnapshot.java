package com.stepfreelondon.monitoring;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One read of every feed. A source missing from {@code observed} could not be read (or only a stale
 * copy was available): its events are neither created nor resolved this cycle, because
 * "TfL didn't answer" must never be read as "the disruption is over".
 */
record FeedSnapshot(Map<DisruptionSource, List<ObservedDisruption>> observed, List<String> failures) {

    FeedSnapshot {
        observed = observed.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(observed));
        failures = List.copyOf(failures);
    }

    boolean wasRead(DisruptionSource source) {
        return observed.containsKey(source);
    }
}
