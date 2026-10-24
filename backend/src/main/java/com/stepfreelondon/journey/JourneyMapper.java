package com.stepfreelondon.journey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import com.stepfreelondon.journey.dto.JourneyLegDto;
import com.stepfreelondon.journey.dto.JourneyLegDto.DisruptionDto;
import com.stepfreelondon.journey.dto.JourneyLegDto.ObstacleDto;
import com.stepfreelondon.journey.dto.JourneyLegDto.PlaceDto;
import com.stepfreelondon.journey.dto.JourneyLegDto.StepDto;
import com.stepfreelondon.journey.dto.JourneyOptionDto;
import com.stepfreelondon.journey.dto.JourneyOptionDto.ObstacleSummaryDto;
import com.stepfreelondon.journey.dto.JourneyPlanDto;
import com.stepfreelondon.line.LineStatusMapper;
import com.stepfreelondon.station.StationNames;
import com.stepfreelondon.tfl.TflDateTimes;
import com.stepfreelondon.tfl.model.TflJourneyResponse;
import com.stepfreelondon.tfl.model.TflJourneyResponse.Journey;
import com.stepfreelondon.tfl.model.TflJourneyResponse.Leg;

@Component
class JourneyMapper {

    private static final Logger log = LoggerFactory.getLogger(JourneyMapper.class);
    static final String WALKING = "walking";

    private final ObjectMapper objectMapper;
    private final LineStatusMapper lineStatusMapper;

    JourneyMapper(ObjectMapper objectMapper, LineStatusMapper lineStatusMapper) {
        this.objectMapper = objectMapper;
        this.lineStatusMapper = lineStatusMapper;
    }

    JourneyPlanDto toPlan(TflJourneyResponse response, List<AccessibilityPreference> applied) {
        List<JourneyOptionDto> journeys = nonNull(response.journeys()).stream().map(this::toOption).toList();
        return new JourneyPlanDto(
                journeys,
                lineStatusMapper.toDtos(nonNull(response.lines())),
                applied,
                nonNull(response.stopMessages()).stream().filter(m -> m != null && !m.isBlank()).toList());
    }

    JourneyOptionDto toOption(Journey journey) {
        List<JourneyLegDto> legs = nonNull(journey.legs()).stream().map(this::toLeg).toList();
        List<JourneyLegDto> rides = legs.stream().filter(leg -> !WALKING.equals(leg.mode())).toList();

        List<String> modes = List.copyOf(new LinkedHashSet<>(legs.stream().map(JourneyLegDto::mode).filter(Objects::nonNull).toList()));
        List<String> lineIds = List.copyOf(new LinkedHashSet<>(rides.stream().map(JourneyLegDto::lineId).filter(Objects::nonNull).toList()));
        int walkingMinutes = legs.stream().filter(leg -> WALKING.equals(leg.mode())).mapToInt(JourneyLegDto::durationMinutes).sum();

        List<ObstacleDto> obstacles = legs.stream().flatMap(leg -> leg.obstacles().stream()).toList();
        ObstacleSummaryDto summary = new ObstacleSummaryDto(
                count(obstacles, "LIFT"), count(obstacles, "STAIRS"), count(obstacles, "ESCALATOR"), count(obstacles, "WALKWAY"));

        return new JourneyOptionDto(
                parseTime(journey.startDateTime()),
                parseTime(journey.arrivalDateTime()),
                journey.duration() == null ? 0 : journey.duration(),
                Math.max(0, rides.size() - 1),
                walkingMinutes,
                modes,
                lineIds,
                legs.stream().anyMatch(JourneyLegDto::disrupted),
                journey.fare() == null ? null : journey.fare().totalCost(),
                summary,
                legs);
    }

    JourneyLegDto toLeg(Leg leg) {
        String mode = leg.mode() == null ? null : leg.mode().id();
        TflJourneyResponse.RouteOption route = nonNull(leg.routeOptions()).stream().findFirst().orElse(null);
        TflJourneyResponse.Identifier line = route == null ? null : route.lineIdentifier();
        boolean walking = WALKING.equals(mode);

        List<PlaceDto> stops = leg.path() == null ? List.of() : nonNull(leg.path().stopPoints()).stream()
                .filter(Objects::nonNull)
                .map(stop -> new PlaceDto(stop.id(), StationNames.displayName(stop.name()), null, null))
                .toList();
        // TfL's stop list ends with the alighting stop; it duplicates "to", so drop it.
        List<PlaceDto> intermediate = stops.isEmpty() ? stops : stops.subList(0, stops.size() - 1);

        List<DisruptionDto> disruptions = nonNull(leg.disruptions()).stream()
                .filter(d -> d != null && d.description() != null && !d.description().isBlank())
                .map(d -> new DisruptionDto(d.category(), d.description().strip(), parseTime(d.lastUpdate())))
                .distinct()
                .toList();

        return new JourneyLegDto(
                mode,
                walking || line == null || isBlank(line.id()) ? null : line.id(),
                walking || line == null || isBlank(line.name()) ? null : line.name(),
                leg.instruction() == null ? null : leg.instruction().summary(),
                leg.instruction() == null ? null : leg.instruction().detailed(),
                parseTime(leg.departureTime()),
                parseTime(leg.arrivalTime()),
                leg.duration() == null ? 0 : leg.duration(),
                leg.distance(),
                toPlace(leg.departurePoint()),
                toPlace(leg.arrivalPoint()),
                walking ? List.of() : intermediate,
                parsePath(leg.path() == null ? null : leg.path().lineString()),
                leg.instruction() == null ? List.of() : nonNull(leg.instruction().steps()).stream()
                        .filter(Objects::nonNull)
                        .map(step -> new StepDto(step.descriptionHeading(), step.description()))
                        .toList(),
                nonNull(leg.obstacles()).stream()
                        .filter(o -> o != null && o.type() != null)
                        .map(o -> new ObstacleDto(o.type().toUpperCase(Locale.ROOT), o.incline(), o.position()))
                        .toList(),
                disruptions,
                Boolean.TRUE.equals(leg.isDisrupted()) || !disruptions.isEmpty());
    }

    /**
     * Parses TfL's {@code lineString} ("[[51.56,-0.27],[51.57,-0.28]]"). Anything malformed yields an
     * empty path: the UI then shows stops and text only, rather than a guessed line.
     */
    List<List<Double>> parsePath(String lineString) {
        if (lineString == null || lineString.isBlank()) {
            return List.of();
        }
        try {
            double[][] points = objectMapper.readValue(lineString, double[][].class);
            List<List<Double>> path = new ArrayList<>(points.length);
            for (double[] point : points) {
                if (point.length != 2 || Math.abs(point[0]) > 90 || Math.abs(point[1]) > 180) {
                    return List.of();
                }
                path.add(List.of(point[0], point[1]));
            }
            return path;
        } catch (JacksonException e) {
            log.warn("Ignoring unparseable TfL lineString ({} chars)", lineString.length());
            return List.of();
        }
    }

    private static PlaceDto toPlace(TflJourneyResponse.Point point) {
        if (point == null) {
            return null;
        }
        return new PlaceDto(point.naptanId(), StationNames.displayName(point.commonName()), point.lat(), point.lon());
    }

    private static Instant parseTime(String value) {
        return TflDateTimes.parse(value).orElse(null);
    }

    private static int count(List<ObstacleDto> obstacles, String type) {
        return (int) obstacles.stream().filter(o -> type.equals(o.type())).count();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
