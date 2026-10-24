package com.stepfreelondon.disruption;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.model.TflDisruptedPoint;
import com.stepfreelondon.tfl.model.TflLiftDisruption;

import static org.assertj.core.api.Assertions.assertThat;

class DisruptionMapperTest {

    private final DisruptionMapper mapper = new DisruptionMapper();

    @Test
    void mapsRealStationDisruptions() {
        List<StationDisruptionDto> disruptions = mapper.toStationDisruptions(
                TflFixtures.read("stop-point-disruptions-tube.json", new TypeReference<List<TflDisruptedPoint>>() { }));

        assertThat(disruptions).isNotEmpty();
        StationDisruptionDto baronsCourt = disruptions.stream()
                .filter(d -> "940GZZLUBSC".equals(d.stationId())).findFirst().orElseThrow();
        assertThat(baronsCourt.stationName()).isEqualTo("Barons Court");
        assertThat(baronsCourt.type()).isEqualTo("Part Closure");
        assertThat(baronsCourt.appearance()).isEqualTo("PlannedWork");
        assertThat(baronsCourt.validFrom()).isEqualTo(Instant.parse("2026-07-06T03:30:00Z"));
        assertThat(baronsCourt.lineIds()).containsExactly("circle", "district", "piccadilly");
        assertThat(disruptions).extracting(StationDisruptionDto::stationName).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void mapsRealLiftOutages() {
        List<LiftOutageDto> outages = mapper.toLiftOutages(
                TflFixtures.read("lift-disruptions.json", new TypeReference<List<TflLiftDisruption>>() { }));

        assertThat(outages).hasSize(18);
        assertThat(outages.getFirst().stationId()).isEqualTo("940GZZLUWYP");
        assertThat(outages.getFirst().liftIds()).containsExactly("940GZZLUWYP-Lift-5");
        assertThat(outages.getFirst().message()).startsWith("WEMBLEY PARK STATION");
    }

    @Test
    void flagsAccessibilityRelatedDescriptionsByKeyword() {
        assertThat(DisruptionMapper.mentionsAccessibility("No lift service between street and ticket hall")).isTrue();
        assertThat(DisruptionMapper.mentionsAccessibility("No Step Free Access at this station")).isTrue();
        assertThat(DisruptionMapper.mentionsAccessibility("Step-free access is available via Bridge Road")).isTrue();
        assertThat(DisruptionMapper.mentionsAccessibility("Escalator 3 is closed")).isTrue();
        assertThat(DisruptionMapper.mentionsAccessibility("Westbound trains will not call here")).isFalse();
        assertThat(DisruptionMapper.mentionsAccessibility("Uplifting music on platform 2")).as("whole words only").isFalse();
    }

    @Test
    void skipsEntriesWithoutDescriptionsOrStations() {
        var blank = new TflDisruptedPoint("940GZZLUXXX", null, "X", " ", null, null, null, null, null, null);
        assertThat(mapper.toStationDisruptions(List.of(blank))).isEmpty();
        assertThat(mapper.toLiftOutages(List.of(new TflLiftDisruption(null, null, "msg")))).isEmpty();
    }
}
