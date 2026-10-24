package com.stepfreelondon.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.stepfreelondon.accessibility.StationProfileService;
import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.disruption.DisruptionService;
import com.stepfreelondon.disruption.LiftOutageService;
import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.line.LineStatusService;
import com.stepfreelondon.tfl.TflUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DisruptionFeedTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    private final LineStatusService lines = mock(LineStatusService.class);
    private final DisruptionService stations = mock(DisruptionService.class);
    private final LiftOutageService lifts = mock(LiftOutageService.class);
    private final StationProfileService profiles = mock(StationProfileService.class);
    private final DisruptionFeed feed = new DisruptionFeed(lines, stations, lifts, profiles, new DisruptionNormaliser());

    @Test
    void readsEveryFeed() {
        given(lines.currentStatus()).willReturn(LiveData.fresh(DisruptionNormaliserTest.lineStatuses(), NOW));
        given(stations.stationDisruptions()).willReturn(LiveData.fresh(DisruptionNormaliserTest.stationDisruptions(), NOW));
        given(lifts.currentOutages()).willReturn(Optional.of(LiveData.fresh(List.of(), NOW)));

        FeedSnapshot snapshot = feed.read();

        assertThat(snapshot.failures()).isEmpty();
        assertThat(snapshot.observed().get(DisruptionSource.LINE_STATUS)).hasSize(8);
        assertThat(snapshot.observed().get(DisruptionSource.STOP_POINT)).isNotEmpty();
        assertThat(snapshot.wasRead(DisruptionSource.LIFT)).isTrue();
    }

    @Test
    void staleUnavailableAndFailingFeedsAreReportedAsNotRead() {
        given(lines.currentStatus()).willReturn(LiveData.fresh(DisruptionNormaliserTest.lineStatuses(), NOW).asStale());
        given(stations.stationDisruptions()).willThrow(new TflUnavailableException("mode-stop-point-disruptions", "down"));
        given(lifts.currentOutages()).willReturn(Optional.empty());

        FeedSnapshot snapshot = feed.read();

        assertThat(snapshot.observed()).isEmpty();
        assertThat(snapshot.failures()).containsExactly(
                "LINE_STATUS: only stale data available", "STOP_POINT: TflUnavailableException", "LIFT: unavailable");
    }

    @Test
    void hubIdsAreExpandedToTheirMemberStations() {
        given(lines.currentStatus()).willReturn(LiveData.fresh(List.of(), NOW));
        given(stations.stationDisruptions()).willReturn(LiveData.fresh(List.of(), NOW));
        given(lifts.currentOutages()).willReturn(Optional.of(LiveData.fresh(List.of(
                new LiftOutageDto("HUBWMB", List.of("HUBWMB-Lift-3"), "Wembley Central: faulty lift"),
                new LiftOutageDto("HUBBRX", List.of("HUBBRX-Lift-1"), "Brixton: faulty lift")), NOW)));
        given(profiles.memberIds("HUBWMB")).willReturn(Set.of("HUBWMB", "940GZZLUWYC", "910GWMBY"));
        given(profiles.memberIds("HUBBRX")).willThrow(new TflUnavailableException("stop-point", "down"));

        List<ObservedDisruption> observed = feed.read().observed().get(DisruptionSource.LIFT);

        assertThat(observed).filteredOn(o -> o.identityKey().equals("HUBWMB")).singleElement()
                .satisfies(o -> assertThat(o.stationIds()).containsExactlyInAnyOrder("HUBWMB", "940GZZLUWYC", "910GWMBY"));
        // A hub that can't be looked up still matches on its own id.
        assertThat(observed).filteredOn(o -> o.identityKey().equals("HUBBRX")).singleElement()
                .satisfies(o -> assertThat(o.stationIds()).containsExactly("HUBBRX"));
    }
}
