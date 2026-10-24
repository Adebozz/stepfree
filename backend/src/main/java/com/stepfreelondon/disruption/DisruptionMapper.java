package com.stepfreelondon.disruption;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;
import com.stepfreelondon.station.StationNames;
import com.stepfreelondon.tfl.TflDateTimes;
import com.stepfreelondon.tfl.model.TflDisruptedPoint;
import com.stepfreelondon.tfl.model.TflLiftDisruption;

@Component
public class DisruptionMapper {

    private static final Pattern ACCESSIBILITY_TERMS =
            Pattern.compile("(?i)\\b(lifts?|escalators?|step[- ]free|wheelchair|ramps?)\\b");

    public List<StationDisruptionDto> toStationDisruptions(List<TflDisruptedPoint> points) {
        return points.stream()
                .filter(p -> p != null && p.description() != null && !p.description().isBlank())
                .map(this::toStationDisruption)
                .distinct()
                .sorted(Comparator.comparing(StationDisruptionDto::stationName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    StationDisruptionDto toStationDisruption(TflDisruptedPoint point) {
        String description = point.description().strip();
        List<String> lineIds = point.concernedLines() == null ? List.of()
                : point.concernedLines().stream().filter(Objects::nonNull).map(TflDisruptedPoint.ConcernedLine::id)
                        .filter(Objects::nonNull).distinct().toList();
        return new StationDisruptionDto(
                point.stationAtcoCode() != null ? point.stationAtcoCode() : point.atcoCode(),
                StationNames.displayName(point.commonName()),
                point.type(),
                point.appearance(),
                description,
                TflDateTimes.parse(point.fromDate()).orElse(null),
                TflDateTimes.parse(point.toDate()).orElse(null),
                lineIds,
                mentionsAccessibility(description));
    }

    public List<LiftOutageDto> toLiftOutages(List<TflLiftDisruption> disruptions) {
        return disruptions.stream()
                .filter(d -> d != null && d.stationUniqueId() != null)
                .map(d -> new LiftOutageDto(
                        d.stationUniqueId(),
                        d.disruptedLiftUniqueIds() == null ? List.of() : List.copyOf(d.disruptedLiftUniqueIds()),
                        d.message() == null ? null : d.message().strip()))
                .toList();
    }

    static boolean mentionsAccessibility(String text) {
        return text != null && ACCESSIBILITY_TERMS.matcher(text).find();
    }
}
