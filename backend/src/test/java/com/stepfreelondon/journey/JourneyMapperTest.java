package com.stepfreelondon.journey;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stepfreelondon.journey.dto.JourneyLegDto;
import com.stepfreelondon.journey.dto.JourneyOptionDto;
import com.stepfreelondon.journey.dto.JourneyPlanDto;
import com.stepfreelondon.line.LineStatusMapper;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.model.TflJourneyResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyMapperTest {

    private final JourneyMapper mapper = new JourneyMapper(TflFixtures.JSON, new LineStatusMapper());

    private JourneyPlanDto stepFreePlan() {
        TflJourneyResponse response = TflFixtures.read("journey-wembley-park-to-kings-cross-step-free.json", TflJourneyResponse.class);
        return mapper.toPlan(response, List.of(AccessibilityPreference.STEP_FREE_TO_PLATFORM));
    }

    @Test
    void mapsJourneySummaryFromRealTflResponse() {
        JourneyPlanDto plan = stepFreePlan();

        assertThat(plan.journeys()).hasSize(3);
        assertThat(plan.appliedAccessibility()).containsExactly(AccessibilityPreference.STEP_FREE_TO_PLATFORM);
        assertThat(plan.lineStatuses()).extracting(l -> l.id()).containsExactly("metropolitan");

        JourneyOptionDto first = plan.journeys().getFirst();
        assertThat(first.durationMinutes()).isEqualTo(34);
        // TfL sends zone-less London times; 17:24 BST is 16:24 UTC.
        assertThat(first.departureTime()).isEqualTo(Instant.parse("2026-10-23T16:24:00Z"));
        assertThat(first.arrivalTime()).isEqualTo(Instant.parse("2026-10-23T16:58:00Z"));
        assertThat(first.modes()).containsExactly("walking", "tube");
        assertThat(first.lineIds()).containsExactly("metropolitan");
        assertThat(first.interchanges()).as("one ride, so no changes").isZero();
        assertThat(first.walkingMinutes()).isEqualTo(12);
        assertThat(first.fareTotalPence()).isEqualTo(360);
        assertThat(first.disrupted()).isFalse();
    }

    @Test
    void countsOnlyObstaclesTflReports() {
        JourneyOptionDto first = stepFreePlan().journeys().getFirst();

        assertThat(first.obstacles().lifts()).isEqualTo(3);
        assertThat(first.obstacles().walkways()).isEqualTo(1);
        assertThat(first.obstacles().stairs()).isZero();
        assertThat(first.obstacles().escalators()).isZero();
    }

    @Test
    void mapsRideLegWithStopsAndRealGeometry() {
        JourneyLegDto tube = stepFreePlan().journeys().getFirst().legs().get(1);

        assertThat(tube.mode()).isEqualTo("tube");
        assertThat(tube.lineId()).isEqualTo("metropolitan");
        assertThat(tube.lineName()).isEqualTo("Metropolitan");
        assertThat(tube.from().id()).isEqualTo("940GZZLUWYP");
        assertThat(tube.from().name()).isEqualTo("Wembley Park");
        assertThat(tube.to().id()).isEqualTo("940GZZLUKSX");
        assertThat(tube.intermediateStops()).as("alighting stop excluded")
                .extracting(JourneyLegDto.PlaceDto::name)
                .containsExactly("Finchley Road", "Baker Street", "Great Portland Street", "Euston Square");
        assertThat(tube.path()).hasSize(264);
        assertThat(tube.path().getFirst()).hasSize(2);
        assertThat(tube.path().getFirst().getFirst()).isBetween(51.0, 52.0);
    }

    @Test
    void walkingLegsHaveNoLineAndNoIntermediateStops() {
        JourneyLegDto walk = stepFreePlan().journeys().getFirst().legs().getFirst();

        assertThat(walk.mode()).isEqualTo("walking");
        assertThat(walk.lineId()).isNull();
        assertThat(walk.lineName()).isNull();
        assertThat(walk.intermediateStops()).isEmpty();
        assertThat(walk.obstacles()).extracting(JourneyLegDto.ObstacleDto::type).containsExactly("WALKWAY", "LIFT");
    }

    @Test
    void surfacesLegDisruptionsFromRealDisruptedJourney() {
        TflJourneyResponse response = TflFixtures.read("journey-euston-to-morden-disrupted.json", TflJourneyResponse.class);

        JourneyOptionDto first = mapper.toPlan(response, List.of()).journeys().getFirst();

        assertThat(first.disrupted()).isTrue();
        assertThat(first.lineIds()).containsExactly("victoria", "northern");
        assertThat(first.interchanges()).isEqualTo(1);
        assertThat(first.legs().getFirst().disruptions()).singleElement()
                .satisfies(d -> assertThat(d.description()).startsWith("Victoria Line: Minor delays"));
    }

    @Test
    void neverInventsGeometryFromBadLineStrings() {
        assertThat(mapper.parsePath(null)).isEmpty();
        assertThat(mapper.parsePath("")).isEmpty();
        assertThat(mapper.parsePath("not json")).isEmpty();
        assertThat(mapper.parsePath("[[51.5,-0.1,3]]")).as("wrong arity").isEmpty();
        assertThat(mapper.parsePath("[[951.5,-0.1]]")).as("out of range").isEmpty();
        assertThat(mapper.parsePath("[[51.5,-0.1],[51.6,-0.2]]")).containsExactly(List.of(51.5, -0.1), List.of(51.6, -0.2));
    }

    @Test
    void emptyTflResponseGivesEmptyPlan() {
        JourneyPlanDto plan = mapper.toPlan(new TflJourneyResponse(null, null, null), List.of());

        assertThat(plan.journeys()).isEmpty();
        assertThat(plan.lineStatuses()).isEmpty();
        assertThat(plan.stopMessages()).isEmpty();
    }
}
