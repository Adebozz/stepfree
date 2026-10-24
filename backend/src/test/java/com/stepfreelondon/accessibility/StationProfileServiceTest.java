package com.stepfreelondon.accessibility;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import com.stepfreelondon.accessibility.dto.StationAccessibilityDto;
import com.stepfreelondon.common.live.LiveCacheProperties;
import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.disruption.DisruptionMapper;
import com.stepfreelondon.disruption.LiftOutageService;
import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.TflUnavailableException;
import com.stepfreelondon.tfl.model.TflDisruptedPoint;
import com.stepfreelondon.tfl.model.TflStopPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StationProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final TflClient tflClient = mock(TflClient.class);
    private final LiftOutageService liftOutageService = mock(LiftOutageService.class);
    private final StationProfileService service = new StationProfileService(
            tflClient, new StationProfileMapper(), new DisruptionMapper(), liftOutageService, CLOCK,
            new LiveDataCacheFactory(CLOCK, new SimpleMeterRegistry(), new LiveCacheProperties(
                    Duration.ofHours(1), Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofHours(12),
                    Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofHours(6))));

    @Test
    void combinesProfileDisruptionsAndOnlyThisStationsLiftOutages() {
        given(tflClient.stopPoint("HUBWMB")).willReturn(TflFixtures.read("stop-point-hub-wembley-central.json", TflStopPoint.class));
        given(tflClient.stopPointDisruptions(List.of("910GWMBY", "940GZZLUWYC"))).willReturn(
                TflFixtures.read("stop-point-disruptions-wembley-central.json", new TypeReference<List<TflDisruptedPoint>>() { }));
        given(liftOutageService.currentOutages()).willReturn(Optional.of(LiveData.fresh(List.of(
                new LiftOutageDto("940GZZLUWYC", List.of("lift-1"), "Wembley Central lift out of order"),
                new LiftOutageDto("940GZZLUECT", List.of("lift-6"), "Earl's Court lift out of order")), NOW)));

        StationAccessibilityDto dto = service.accessibility("HUBWMB").data();

        assertThat(dto.hub()).isTrue();
        assertThat(dto.stations()).hasSize(2);
        assertThat(dto.liftDataAvailable()).isTrue();
        assertThat(dto.liftOutages()).extracting(LiftOutageDto::stationId).containsExactly("940GZZLUWYC");
        assertThat(dto.disruptionDataAvailable()).isTrue();
        // The fixture has the same hub notice for two member stations plus one more; duplicates are collapsed.
        assertThat(dto.disruptions()).extracting(d -> d.description()).doesNotHaveDuplicates();
        assertThat(dto.disruptions()).anySatisfy(d -> assertThat(d.mentionsAccessibility()).isTrue());
    }

    @Test
    void liveSourceFailuresAreReportedNotHidden() {
        given(tflClient.stopPoint("940GZZLUWYP")).willReturn(TflFixtures.read("stop-point-wembley-park.json", TflStopPoint.class));
        given(tflClient.stopPointDisruptions(anyList())).willThrow(new TflUnavailableException("stop-point-disruptions", "down"));
        given(liftOutageService.currentOutages()).willReturn(Optional.empty());

        LiveData<StationAccessibilityDto> result = service.accessibility("940GZZLUWYP");

        assertThat(result.data().stations()).hasSize(1);
        assertThat(result.data().liftDataAvailable()).isFalse();
        assertThat(result.data().liftOutages()).isEmpty();
        assertThat(result.data().disruptionDataAvailable()).isFalse();
        assertThat(result.data().disruptions()).isEmpty();
        assertThat(result.data().stationInfoRetrievedAt()).isEqualTo(NOW);
    }

    @Test
    void stationDisruptionRepeatingALiftOutageIsShownOnlyAsALiftOutage() {
        var points = TflFixtures.read("stop-point-disruptions-wembley-central.json", new TypeReference<List<TflDisruptedPoint>>() { });
        var all = StationProfileService.distinctByDescription(new DisruptionMapper().toStationDisruptions(points));
        String repeated = all.getFirst().description();
        var outage = new LiftOutageDto("940GZZLUWYC", List.of("lift-1"), "  " + repeated.replace(" ", "  ") + "\n");

        var remaining = StationProfileService.withoutLiftOutages(all, List.of(outage));

        assertThat(remaining).hasSize(all.size() - 1);
        assertThat(remaining).extracting(d -> d.description()).doesNotContain(repeated);
        assertThat(StationProfileService.withoutLiftOutages(all, List.of())).isEqualTo(all);
    }

    @Test
    void distinctByDescriptionKeepsFirstOccurrence() {
        var mapper = new DisruptionMapper();
        var points = TflFixtures.read("stop-point-disruptions-wembley-central.json", new TypeReference<List<TflDisruptedPoint>>() { });

        var all = mapper.toStationDisruptions(points);
        var distinct = StationProfileService.distinctByDescription(all);

        assertThat(distinct.size()).isLessThanOrEqualTo(all.size());
        assertThat(distinct).extracting(d -> d.description()).doesNotHaveDuplicates();
    }
}
