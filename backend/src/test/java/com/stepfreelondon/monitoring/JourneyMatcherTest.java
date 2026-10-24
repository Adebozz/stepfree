package com.stepfreelondon.monitoring;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Wembley Park → Baker Street (Metropolitan), change, → Oxford Circus (Bakerloo). */
class JourneyMatcherTest {

    private static final MonitoredJourney JOURNEY = new MonitoredJourney(UUID.randomUUID(), UUID.randomUUID(), "Commute",
            Set.of("metropolitan", "bakerloo"),
            Map.of("940GZZLUWYP", "ORIGIN", "940GZZLUFYR", "INTERMEDIATE", "940GZZLUBST", "INTERCHANGE",
                    "940GZZLURGP", "INTERMEDIATE", "940GZZLUOXC", "DESTINATION"));

    @Test
    void lineStatusMatchesOnlyLinesTheRouteRides() {
        assertThat(matches(event(DisruptionSource.LINE_STATUS, Set.of("metropolitan"), Set.of()))).isTrue();
        // Jubilee also serves Wembley Park and Baker Street, but this route doesn't ride it.
        assertThat(matches(event(DisruptionSource.LINE_STATUS, Set.of("jubilee"), Set.of()))).isFalse();
    }

    @Test
    void stationNoticeNeedsTheStationAndARiddenLineIfItNamesLines() {
        assertThat(matches(event(DisruptionSource.STOP_POINT, Set.of(), Set.of("940GZZLUBST")))).isTrue();
        assertThat(matches(event(DisruptionSource.STOP_POINT, Set.of("bakerloo"), Set.of("940GZZLURGP")))).isTrue();
        // A Circle line closure listed at Baker Street doesn't affect a Metropolitan/Bakerloo journey.
        assertThat(matches(event(DisruptionSource.STOP_POINT, Set.of("circle", "hammersmith-city"), Set.of("940GZZLUBST")))).isFalse();
        assertThat(matches(event(DisruptionSource.STOP_POINT, Set.of(), Set.of("940GZZLUKSX")))).isFalse();
    }

    @Test
    void liftOutageMattersOnlyWhereThePassengerLeavesTheTrain() {
        assertThat(matches(event(DisruptionSource.LIFT, Set.of(), Set.of("940GZZLUWYP")))).isTrue();
        assertThat(matches(event(DisruptionSource.LIFT, Set.of(), Set.of("940GZZLUBST")))).isTrue();
        assertThat(matches(event(DisruptionSource.LIFT, Set.of(), Set.of("940GZZLUOXC")))).isTrue();
        // Finchley Road is ridden through; its lifts aren't used.
        assertThat(matches(event(DisruptionSource.LIFT, Set.of(), Set.of("940GZZLUFYR")))).isFalse();
    }

    @Test
    void liftNoticeRepeatedAsAStationNoticeAlertsOnce() {
        String text = "WEMBLEY PARK STATION: no lift service between the street and ticket hall.";
        ActiveEvent lift = new ActiveEvent(UUID.randomUUID(), DisruptionSource.LIFT, "940GZZLUWYP", "h", 1, "Lift out of service",
                text, Set.of(), Set.of("940GZZLUWYP"));
        ActiveEvent notice = new ActiveEvent(UUID.randomUUID(), DisruptionSource.STOP_POINT, "k", "h", 1, "Interchange Message",
                " " + text.replace(" ", "  "), Set.of(), Set.of("940GZZLUWYP"));

        assertThat(JourneyMatcher.match(List.of(JOURNEY), List.of(lift, notice)))
                .extracting(m -> m.event().source()).containsExactly(DisruptionSource.LIFT);
    }

    @Test
    void eachJourneyIsMatchedIndependently() {
        MonitoredJourney other = new MonitoredJourney(UUID.randomUUID(), UUID.randomUUID(), "Other", Set.of("jubilee"),
                Map.of("940GZZLUWYP", "ORIGIN"));
        ActiveEvent metropolitan = event(DisruptionSource.LINE_STATUS, Set.of("metropolitan"), Set.of());

        assertThat(JourneyMatcher.match(List.of(JOURNEY, other), List.of(metropolitan)))
                .extracting(m -> m.journey().name()).containsExactly("Commute");
    }

    private static boolean matches(ActiveEvent event) {
        return JourneyMatcher.affects(event, JOURNEY);
    }

    private static ActiveEvent event(DisruptionSource source, Set<String> lines, Set<String> stations) {
        return new ActiveEvent(UUID.randomUUID(), source, "k", "h", 1, "t", "d", lines, stations);
    }
}
