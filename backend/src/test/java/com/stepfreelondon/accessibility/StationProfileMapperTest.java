package com.stepfreelondon.accessibility;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stepfreelondon.accessibility.dto.StationAccessibilityDto.FactDto;
import com.stepfreelondon.accessibility.dto.StationAccessibilityDto.SectionDto;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.model.TflStopPoint;

import static org.assertj.core.api.Assertions.assertThat;

class StationProfileMapperTest {

    private final StationProfileMapper mapper = new StationProfileMapper();

    @Test
    void mapsStationWithPublishedAccessibilityData() {
        StationProfile profile = mapper.toProfile(TflFixtures.read("stop-point-wembley-park.json", TflStopPoint.class));

        assertThat(profile.detail().id()).isEqualTo("940GZZLUWYP");
        assertThat(profile.detail().name()).isEqualTo("Wembley Park");
        assertThat(profile.detail().icsCode()).isEqualTo("1000257");
        assertThat(profile.detail().hub()).isFalse();
        assertThat(profile.detail().lines()).extracting(l -> l.id()).contains("jubilee", "metropolitan");
        assertThat(profile.memberIds()).containsExactly("940GZZLUWYP");

        SectionDto section = profile.sections().getFirst();
        Map<String, String> facts = asMap(section.accessibility());
        assertThat(facts).containsEntry("AccessViaLift", "Yes").containsEntry("SpecificEntranceRequired", "No");
        assertThat(section.additionalInformation()).startsWith("If you are travelling to Wembley Park");
        assertThat(section.facilities()).extracting(FactDto::key).as("priority facilities first")
                .startsWith("Lifts", "Escalators", "Boarding Ramps", "Toilets");
        assertThat(section.facilities().getFirst().value()).isEqualTo("5");
    }

    @Test
    void hubBecomesOneSectionPerMemberStationWithUnavailableFactsKept() {
        StationProfile profile = mapper.toProfile(TflFixtures.read("stop-point-hub-wembley-central.json", TflStopPoint.class));

        assertThat(profile.detail().hub()).isTrue();
        assertThat(profile.sections()).extracting(SectionDto::id).containsExactly("910GWMBY", "940GZZLUWYC");
        assertThat(profile.memberIds()).containsExactly("910GWMBY", "940GZZLUWYC");

        // TfL publishes no Accessibility properties for Wembley Central: every known field is present with a null value.
        SectionDto tube = profile.sections().get(1);
        assertThat(tube.accessibility()).hasSize(StationProfileMapper.ACCESSIBILITY_LABELS.size());
        assertThat(tube.accessibility()).allSatisfy(fact -> assertThat(fact.value()).isNull());
        assertThat(tube.additionalInformation()).isNull();
    }

    @Test
    void keepsUnknownAccessibilityKeysAndTreatsBlankAsUnavailable() {
        var stop = new TflStopPoint("940GZZLUXXX", "Test Underground Station", "1", "NaptanMetroStation", List.of("tube", "bus"),
                null, null, null, List.of(
                new TflStopPoint.Property("Accessibility", "AccessViaLift", " "),
                new TflStopPoint.Property("Accessibility", "NewTflField", "Maybe"),
                new TflStopPoint.Property("Address", "Address", "1 Road")), null);

        SectionDto section = mapper.toSection(stop);
        Map<String, String> facts = asMap(section.accessibility());

        assertThat(facts.get("AccessViaLift")).isNull();
        assertThat(facts).containsEntry("NewTflField", "Maybe");
        assertThat(section.modes()).containsExactly("tube");
        assertThat(section.facilities()).isEmpty();
    }

    private static Map<String, String> asMap(List<FactDto> facts) {
        var map = new java.util.HashMap<String, String>();
        facts.forEach(f -> map.put(f.key(), f.value()));
        return map;
    }
}
