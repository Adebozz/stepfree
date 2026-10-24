package com.stepfreelondon.accessibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stepfreelondon.accessibility.dto.StationAccessibilityDto.FactDto;
import com.stepfreelondon.accessibility.dto.StationAccessibilityDto.SectionDto;
import com.stepfreelondon.accessibility.dto.StationDetailDto;
import com.stepfreelondon.accessibility.dto.StationDetailDto.LineRefDto;
import com.stepfreelondon.station.StationNames;
import com.stepfreelondon.tfl.model.TflStopPoint;

@Component
class StationProfileMapper {

    /** Member-station stop types inside a hub. Platforms, entrances and bus stops are not sections. */
    private static final Set<String> STATION_TYPES = Set.of("NaptanMetroStation", "NaptanRailStation");
    private static final Set<String> RAIL_MODES = Set.of("tube", "overground", "elizabeth-line", "dlr", "national-rail", "tram");

    /**
     * TfL's Accessibility keys (source LRAD) with plain-English labels, in display order. Every key is
     * always emitted so the UI can show "Information unavailable" rather than silently omitting it.
     */
    static final Map<String, String> ACCESSIBILITY_LABELS = orderedMap(
            "AccessViaLift", "Step-free access via lift",
            "SpecificEntranceRequired", "Specific entrance needed for step-free access",
            "LimitedCapacityLift", "Lift has limited capacity",
            "Toilet", "Accessible toilet",
            "BlueBadgeCarParkSpaces", "Blue Badge parking spaces",
            "TaxiRankOutsideStation", "Taxi rank outside station");

    /** TfL's own (misspelt) key for free-text notes. */
    static final String ADDITIONAL_INFORMATION_KEY = "AddtionalInformation";

    private static final List<String> FACILITY_PRIORITY =
            List.of("Lifts", "Escalators", "Boarding Ramps", "Toilets", "Help Points", "Waiting Room");

    StationProfile toProfile(TflStopPoint stop) {
        boolean hub = isHub(stop);
        List<TflStopPoint> members = hub
                ? nonNull(stop.children()).stream().filter(c -> c != null && STATION_TYPES.contains(c.stopType())).toList()
                : List.of();
        List<TflStopPoint> sectionSources = members.isEmpty() ? List.of(stop) : members;

        List<SectionDto> sections = sectionSources.stream().map(this::toSection).toList();
        List<String> memberIds = sectionSources.stream().map(TflStopPoint::naptanId).filter(Objects::nonNull).toList();

        StationDetailDto detail = new StationDetailDto(
                stop.naptanId(),
                StationNames.displayName(stop.commonName()),
                stop.icsCode(),
                railModes(stop.modes()),
                nonNull(stop.lines()).stream().filter(l -> l != null && l.id() != null)
                        .map(l -> new LineRefDto(l.id(), l.name())).toList(),
                stop.lat(),
                stop.lon(),
                hub);
        return new StationProfile(detail, sections, memberIds);
    }

    SectionDto toSection(TflStopPoint stop) {
        Map<String, String> accessibility = new LinkedHashMap<>();
        List<FactDto> facilities = new ArrayList<>();
        for (TflStopPoint.Property property : nonNull(stop.additionalProperties())) {
            if (property == null || property.key() == null) {
                continue;
            }
            String value = property.value() == null || property.value().isBlank() ? null : property.value().strip();
            if ("Accessibility".equalsIgnoreCase(property.category())) {
                accessibility.putIfAbsent(property.key(), value);
            } else if ("Facility".equalsIgnoreCase(property.category())) {
                facilities.add(new FactDto(property.key(), property.key(), value));
            }
        }

        List<FactDto> facts = new ArrayList<>();
        ACCESSIBILITY_LABELS.forEach((key, label) -> facts.add(new FactDto(key, label, accessibility.get(key))));
        // Keys TfL adds in future are still shown, labelled by their raw key.
        accessibility.forEach((key, value) -> {
            if (!ACCESSIBILITY_LABELS.containsKey(key) && !ADDITIONAL_INFORMATION_KEY.equals(key)) {
                facts.add(new FactDto(key, key, value));
            }
        });

        facilities.sort(Comparator.comparingInt((FactDto f) -> facilityRank(f.key())).thenComparing(FactDto::key));

        return new SectionDto(
                stop.naptanId(),
                StationNames.displayName(stop.commonName()),
                railModes(stop.modes()),
                facts,
                accessibility.get(ADDITIONAL_INFORMATION_KEY),
                facilities);
    }

    private static boolean isHub(TflStopPoint stop) {
        return "TransportInterchange".equals(stop.stopType()) || (stop.naptanId() != null && stop.naptanId().startsWith("HUB"));
    }

    private static List<String> railModes(List<String> modes) {
        return nonNull(modes).stream().filter(RAIL_MODES::contains).sorted().toList();
    }

    private static int facilityRank(String key) {
        int index = FACILITY_PRIORITY.indexOf(key);
        return index < 0 ? FACILITY_PRIORITY.size() : index;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
