package com.stepfreelondon.monitoring;

import java.time.Instant;
import java.util.Set;

/**
 * A disruption as seen in one read of a TfL feed, normalised so reads can be compared.
 *
 * @param identityKey which disruption this is; stable while TfL keeps reporting it
 * @param contentHash SHA-256 of the user-visible text; a change means the disruption was updated
 * @param lineIds     lines it affects (empty if TfL names none)
 * @param stationIds  stations it affects, as NaPTAN ids (empty for line-wide events)
 */
record ObservedDisruption(
        DisruptionSource source,
        String identityKey,
        String contentHash,
        String category,
        String type,
        Integer severityCode,
        String severityDescription,
        String title,
        String description,
        Instant validFrom,
        Instant validTo,
        Set<String> lineIds,
        Set<String> stationIds) {

    ObservedDisruption {
        lineIds = Set.copyOf(lineIds);
        stationIds = Set.copyOf(stationIds);
    }
}
