package com.stepfreelondon.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares the active events in the database with one feed snapshot. Pure, so every rule is unit-tested:
 * <ul>
 *   <li>key not active → <b>created</b></li>
 *   <li>key active, different content hash → <b>updated</b> (revision + 1)</li>
 *   <li>key active, same hash → <b>unchanged</b> (only {@code last_observed_at} moves; the affected
 *       lines/stations are rewritten if TfL changed them, without a new revision)</li>
 *   <li>key active, absent from a source that <em>was read</em> → <b>resolved</b></li>
 *   <li>source not read → its events are left exactly as they are</li>
 * </ul>
 */
final class ChangeDetector {

    record Update(ActiveEvent event, ObservedDisruption observed) {
    }

    record Changes(List<ObservedDisruption> created, List<Update> updated, List<Update> unchanged, List<ActiveEvent> resolved) {

        boolean scopeChanged(Update u) {
            return !u.event().lineIds().equals(u.observed().lineIds()) || !u.event().stationIds().equals(u.observed().stationIds());
        }
    }

    private ChangeDetector() {
    }

    static Changes diff(List<ActiveEvent> active, FeedSnapshot snapshot) {
        Map<String, ActiveEvent> activeByKey = new HashMap<>();
        active.forEach(event -> activeByKey.put(key(event.source(), event.identityKey()), event));

        List<ObservedDisruption> created = new ArrayList<>();
        List<Update> updated = new ArrayList<>();
        List<Update> unchanged = new ArrayList<>();

        // A feed can list one identity twice (e.g. duplicate rows); the first one wins.
        Map<String, ObservedDisruption> observedByKey = new LinkedHashMap<>();
        snapshot.observed().values().forEach(list -> list.forEach(o -> observedByKey.putIfAbsent(key(o.source(), o.identityKey()), o)));

        observedByKey.forEach((key, observed) -> {
            ActiveEvent existing = activeByKey.get(key);
            if (existing == null) {
                created.add(observed);
            } else if (existing.contentHash().equals(observed.contentHash())) {
                unchanged.add(new Update(existing, observed));
            } else {
                updated.add(new Update(existing, observed));
            }
        });

        List<ActiveEvent> resolved = active.stream()
                .filter(event -> snapshot.wasRead(event.source()))
                .filter(event -> !observedByKey.containsKey(key(event.source(), event.identityKey())))
                .toList();
        return new Changes(created, updated, unchanged, resolved);
    }

    private static String key(DisruptionSource source, String identityKey) {
        return source + "\u0000" + identityKey;
    }
}
