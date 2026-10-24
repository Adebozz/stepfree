package com.stepfreelondon.monitoring;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.stepfreelondon.accessibility.StationProfileService;
import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.disruption.DisruptionService;
import com.stepfreelondon.disruption.LiftOutageService;
import com.stepfreelondon.line.LineStatusService;
import com.stepfreelondon.tfl.TflException;

/**
 * Reads the three TfL feeds through the same caches the UI uses, so monitoring adds no TfL calls
 * while someone is browsing. Runs outside any database transaction.
 */
@Component
class DisruptionFeed {

    private static final Logger log = LoggerFactory.getLogger(DisruptionFeed.class);
    private static final String HUB_PREFIX = "HUB";

    private final LineStatusService lineStatusService;
    private final DisruptionService disruptionService;
    private final LiftOutageService liftOutageService;
    private final StationProfileService stationProfileService;
    private final DisruptionNormaliser normaliser;

    DisruptionFeed(LineStatusService lineStatusService, DisruptionService disruptionService,
                   LiftOutageService liftOutageService, StationProfileService stationProfileService,
                   DisruptionNormaliser normaliser) {
        this.lineStatusService = lineStatusService;
        this.disruptionService = disruptionService;
        this.liftOutageService = liftOutageService;
        this.stationProfileService = stationProfileService;
        this.normaliser = normaliser;
    }

    FeedSnapshot read() {
        Map<DisruptionSource, List<ObservedDisruption>> observed = new EnumMap<>(DisruptionSource.class);
        List<String> failures = new ArrayList<>();
        read(DisruptionSource.LINE_STATUS, () -> Optional.of(lineStatusService.currentStatus()),
                normaliser::fromLineStatuses, observed, failures);
        read(DisruptionSource.STOP_POINT, () -> Optional.of(disruptionService.stationDisruptions()),
                normaliser::fromStationDisruptions, observed, failures);
        read(DisruptionSource.LIFT, liftOutageService::currentOutages, normaliser::fromLiftOutages, observed, failures);
        observed.replaceAll((source, events) -> events.stream().map(this::withHubMembers).toList());
        return new FeedSnapshot(observed, failures);
    }

    /**
     * TfL's lift feed often names a hub ({@code HUBWMB}) while saved routes hold the platform-level ids
     * of the legs ({@code 940GZZLUWYC}). Adding the hub's members lets an interchange there match.
     * Only the scope changes, never the identity or hash, so this can't create a new revision.
     */
    private ObservedDisruption withHubMembers(ObservedDisruption o) {
        if (o.stationIds().stream().noneMatch(id -> id.startsWith(HUB_PREFIX))) {
            return o;
        }
        Set<String> stations = new HashSet<>(o.stationIds());
        for (String id : o.stationIds()) {
            if (id.startsWith(HUB_PREFIX)) {
                try {
                    stations.addAll(stationProfileService.memberIds(id));
                } catch (TflException e) {
                    log.warn("Could not expand hub {}; matching on the hub id only: {}", id, e.getMessage());
                }
            }
        }
        return new ObservedDisruption(o.source(), o.identityKey(), o.contentHash(), o.category(), o.type(), o.severityCode(),
                o.severityDescription(), o.title(), o.description(), o.validFrom(), o.validTo(), o.lineIds(), stations);
    }

    private static <T> void read(DisruptionSource source, Supplier<Optional<LiveData<T>>> fetch,
                                 Function<T, List<ObservedDisruption>> normalise,
                                 Map<DisruptionSource, List<ObservedDisruption>> observed, List<String> failures) {
        try {
            Optional<LiveData<T>> data = fetch.get();
            if (data.isEmpty()) {
                failures.add(source + ": unavailable");
            } else if (data.get().stale()) {
                // A stale copy is the last good read, not the current state; acting on it could resolve or
                // re-create events based on old data.
                failures.add(source + ": only stale data available");
            } else {
                observed.put(source, normalise.apply(data.get().data()));
            }
        } catch (TflException e) {
            log.warn("Monitoring could not read {}: {}", source, e.getMessage());
            failures.add(source + ": " + e.getClass().getSimpleName());
        }
    }
}
