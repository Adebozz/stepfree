package com.stepfreelondon.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import com.stepfreelondon.disruption.DisruptionMapper;
import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;
import com.stepfreelondon.line.LineStatusMapper;
import com.stepfreelondon.line.ServiceLevel;
import com.stepfreelondon.line.dto.LineStatusDto;
import com.stepfreelondon.line.dto.LineStatusEntryDto;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.model.TflDisruptedPoint;
import com.stepfreelondon.tfl.model.TflLiftDisruption;
import com.stepfreelondon.tfl.model.TflLine;

import static org.assertj.core.api.Assertions.assertThat;

class DisruptionNormaliserTest {

    private final DisruptionNormaliser normaliser = new DisruptionNormaliser();

    static List<LineStatusDto> lineStatuses() {
        return new LineStatusMapper().toDtos(TflFixtures.read("line-status-2026-10-23.json", new TypeReference<List<TflLine>>() { }));
    }

    static List<StationDisruptionDto> stationDisruptions() {
        return new DisruptionMapper().toStationDisruptions(TflFixtures.read("stop-point-disruptions-modes-2026-10-24.json",
                new TypeReference<List<TflDisruptedPoint>>() { }));
    }

    static List<LiftOutageDto> liftOutages() {
        return new DisruptionMapper().toLiftOutages(TflFixtures.read("lift-disruptions.json",
                new TypeReference<List<TflLiftDisruption>>() { }));
    }

    @Test
    void onlyDisruptedLinesBecomeEventsKeyedByLineId() {
        List<ObservedDisruption> events = normaliser.fromLineStatuses(lineStatuses());

        // The 2026-10-23 snapshot: 8 lines at minor delays or worse, 12 at good service.
        assertThat(events).extracting(ObservedDisruption::identityKey)
                .containsExactlyInAnyOrder("central", "elizabeth", "northern", "piccadilly", "suffragette", "tram", "victoria", "windrush");
        ObservedDisruption northern = events.stream().filter(e -> e.identityKey().equals("northern")).findFirst().orElseThrow();
        assertThat(northern.title()).isEqualTo("Northern: Severe Delays");
        assertThat(northern.severityCode()).isEqualTo(6);
        assertThat(northern.lineIds()).containsExactly("northern");
        assertThat(northern.stationIds()).isEmpty();
        assertThat(northern.description()).isNotBlank().doesNotContain("\n ");
    }

    @Test
    void worseningSeverityIsAnUpdateOfTheSameEvent() {
        LineStatusDto minor = line(9, "Minor Delays", ServiceLevel.MINOR_DISRUPTION, "Minor delays due to a signal failure.");
        LineStatusDto severe = line(6, "Severe Delays", ServiceLevel.SEVERE_DISRUPTION, "Severe delays due to a signal failure.");
        LineStatusDto sameAgain = line(9, "Minor Delays", ServiceLevel.MINOR_DISRUPTION, "Minor  delays due to a signal failure. ");

        ObservedDisruption a = normaliser.fromLineStatuses(List.of(minor)).getFirst();
        ObservedDisruption b = normaliser.fromLineStatuses(List.of(severe)).getFirst();
        ObservedDisruption c = normaliser.fromLineStatuses(List.of(sameAgain)).getFirst();

        assertThat(b.identityKey()).isEqualTo(a.identityKey());
        assertThat(b.contentHash()).isNotEqualTo(a.contentHash());
        // Whitespace differences are not a content change.
        assertThat(c.contentHash()).isEqualTo(a.contentHash());
    }

    @Test
    void informationAndUnknownStatusesCreateNoEvent() {
        assertThat(normaliser.fromLineStatuses(List.of(
                line(19, "Information", ServiceLevel.INFORMATION, "Engineering works next weekend."),
                new LineStatusDto("jubilee", "Jubilee", "tube", ServiceLevel.UNKNOWN, "Status unavailable", List.of())))).isEmpty();
    }

    @Test
    void aNoticeRepeatedAtManyStationsIsOneEvent() {
        List<StationDisruptionDto> rows = stationDisruptions();
        List<ObservedDisruption> events = normaliser.fromStationDisruptions(rows);

        // 141 rows on 2026-10-24 for 62 distinct texts; grouping must land close to the distinct count, not the row count.
        assertThat(rows).hasSizeGreaterThan(events.size());
        ObservedDisruption circle = events.stream().filter(e -> e.description().startsWith("CIRCLE LINE")).findFirst().orElseThrow();
        assertThat(circle.stationIds()).hasSize(18).contains("940GZZLUVIC", "940GZZLUPAC");
        assertThat(circle.title()).isEqualTo("Part Closure");
        assertThat(events.stream().filter(e -> e.description().startsWith("CIRCLE LINE"))).hasSize(1);
    }

    @Test
    void lineNamedInTheHeadingIsUsedWhenTflListsNoLines() {
        ObservedDisruption circle = normaliser.fromStationDisruptions(stationDisruptions()).stream()
                .filter(e -> e.description().startsWith("CIRCLE LINE")).findFirst().orElseThrow();
        assertThat(circle.lineIds()).containsExactly("circle");

        assertThat(DisruptionNormaliser.linesOf(station("940GZZLUKSX", "HAMMERSMITH & CITY LINE: no service", List.of())))
                .containsExactly("hammersmith-city");
        assertThat(DisruptionNormaliser.linesOf(station("940GZZLUKSX", "MADE UP LINE: something", List.of()))).isEmpty();
        assertThat(DisruptionNormaliser.linesOf(station("940GZZLUKSX", "KING'S CROSS STATION: lift closed", List.of()))).isEmpty();
        assertThat(DisruptionNormaliser.linesOf(station("940GZZLUKSX", "CIRCLE LINE: x", List.of("district")))).containsExactly("district");
    }

    @Test
    void singleStationNoticeIsTitledWithTheStation() {
        ObservedDisruption wembley = normaliser.fromStationDisruptions(stationDisruptions()).stream()
                .filter(e -> e.stationIds().equals(Set.of("940GZZLUWYP")) && e.description().startsWith("WEMBLEY PARK"))
                .findFirst().orElseThrow();
        assertThat(wembley.title()).isEqualTo("Interchange Message at Wembley Park");
        assertThat(wembley.validFrom()).isEqualTo(Instant.parse("2026-03-10T04:30:00Z"));
    }

    @Test
    void identityAndHashDoNotDependOnFeedOrder() {
        List<StationDisruptionDto> rows = new ArrayList<>(stationDisruptions());
        List<ObservedDisruption> before = normaliser.fromStationDisruptions(rows);
        Collections.shuffle(rows, new Random(42));
        List<ObservedDisruption> after = normaliser.fromStationDisruptions(rows);

        assertThat(after).extracting(e -> e.identityKey() + e.contentHash())
                .containsExactlyInAnyOrderElementsOf(before.stream().map(e -> e.identityKey() + e.contentHash()).toList());
        assertThat(before).extracting(ObservedDisruption::identityKey).doesNotHaveDuplicates();
    }

    @Test
    void editedTextKeepsIdentityWhenTheNoticeHasAHeading() {
        StationDisruptionDto original = station("940GZZLUPCO", "PIMLICO STATION: no down escalator until late December.", List.of());
        StationDisruptionDto edited = station("940GZZLUPCO", "PIMLICO STATION: no down escalator until mid January.", List.of());

        ObservedDisruption a = normaliser.fromStationDisruptions(List.of(original)).getFirst();
        ObservedDisruption b = normaliser.fromStationDisruptions(List.of(edited)).getFirst();

        assertThat(b.identityKey()).isEqualTo(a.identityKey());
        assertThat(b.contentHash()).isNotEqualTo(a.contentHash());
        assertThat(DisruptionNormaliser.heading(original.description())).isEqualTo("PIMLICO STATION");
    }

    @Test
    void liftOutagesAreOneEventPerStationWithLiftsAsContent() {
        List<ObservedDisruption> events = normaliser.fromLiftOutages(liftOutages());

        // 18 rows, but Harold Wood (910GHRLDWOD) appears twice.
        assertThat(events).hasSize(17);
        ObservedDisruption hub = events.stream().filter(e -> e.identityKey().equals("HUBWMB")).findFirst().orElseThrow();
        assertThat(hub.stationIds()).containsExactly("HUBWMB");
        assertThat(hub.description()).startsWith("Wembley Central: No Step Free Access");

        LiftOutageDto oneLift = new LiftOutageDto("940GZZLUWYP", List.of("lift-5"), "Lift 5 out of service");
        LiftOutageDto twoLifts = new LiftOutageDto("940GZZLUWYP", List.of("lift-5", "lift-6"), "Lift 5 out of service");
        ObservedDisruption a = normaliser.fromLiftOutages(List.of(oneLift)).getFirst();
        ObservedDisruption b = normaliser.fromLiftOutages(List.of(twoLifts)).getFirst();
        assertThat(b.identityKey()).isEqualTo(a.identityKey());
        assertThat(b.contentHash()).isNotEqualTo(a.contentHash());
        assertThat(b.title()).isEqualTo("Lifts out of service");
    }

    private static LineStatusDto line(int code, String description, ServiceLevel level, String reason) {
        var entry = new LineStatusEntryDto(code, description, level, reason, null, null, false);
        return new LineStatusDto("metropolitan", "Metropolitan", "tube", level, description, List.of(entry));
    }

    private static StationDisruptionDto station(String id, String description, List<String> lines) {
        return new StationDisruptionDto(id, "Pimlico", "Interchange Message", "PlannedWork", description,
                Instant.parse("2026-10-09T03:30:00Z"), null, lines, false);
    }
}
