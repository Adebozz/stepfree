package com.stepfreelondon.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which monitored journeys an active event affects, using only the route the user saved
 * (risk R6: a journey is not "affected" just because some line serving its stations is disrupted).
 *
 * <ul>
 *   <li><b>Line status</b>: the journey rides that line.</li>
 *   <li><b>Station notice</b>: the journey passes the station, and the notice either names no lines
 *       or names one the journey rides. "CIRCLE LINE: no service…" listed at Victoria doesn't affect a
 *       Victoria line journey through Victoria.</li>
 *   <li><b>Lift outage</b>: the journey starts, ends or changes at the station. Riding through
 *       without leaving the train doesn't need the lift.</li>
 * </ul>
 * TfL often publishes a lift notice also as a station notice with the same text; the journey is then
 * alerted once, by the lift event.
 */
final class JourneyMatcher {

    record Match(MonitoredJourney journey, ActiveEvent event) {
    }

    private JourneyMatcher() {
    }

    static List<Match> match(List<MonitoredJourney> journeys, List<ActiveEvent> events) {
        List<Match> matches = new ArrayList<>();
        for (MonitoredJourney journey : journeys) {
            List<ActiveEvent> affecting = events.stream().filter(e -> affects(e, journey)).toList();
            Set<String> liftTexts = affecting.stream()
                    .filter(e -> e.source() == DisruptionSource.LIFT)
                    .map(e -> DisruptionNormaliser.normalise(e.description()))
                    .collect(Collectors.toSet());
            affecting.stream()
                    .filter(e -> e.source() != DisruptionSource.STOP_POINT
                            || !liftTexts.contains(DisruptionNormaliser.normalise(e.description())))
                    .forEach(e -> matches.add(new Match(journey, e)));
        }
        return matches;
    }

    static boolean affects(ActiveEvent event, MonitoredJourney journey) {
        return switch (event.source()) {
            case LINE_STATUS -> !Collections.disjoint(event.lineIds(), journey.lineIds());
            case STOP_POINT -> event.stationIds().stream().anyMatch(journey::passesStation)
                    && (event.lineIds().isEmpty() || !Collections.disjoint(event.lineIds(), journey.lineIds()));
            case LIFT -> event.stationIds().stream().anyMatch(journey::usesStation);
        };
    }
}
