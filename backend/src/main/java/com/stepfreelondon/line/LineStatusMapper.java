package com.stepfreelondon.line;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.stepfreelondon.line.dto.LineStatusDto;
import com.stepfreelondon.line.dto.LineStatusEntryDto;
import com.stepfreelondon.tfl.TflDateTimes;
import com.stepfreelondon.tfl.model.TflLine;

@Component
public class LineStatusMapper {

    static final String STATUS_UNAVAILABLE = "Status unavailable";

    /** Display order of modes; lines within a mode are alphabetical. */
    private static final List<String> MODE_ORDER = List.of("tube", "elizabeth-line", "overground", "dlr", "tram");

    private static final Comparator<LineStatusEntryDto> MOST_SERIOUS_FIRST =
            Comparator.comparing(LineStatusEntryDto::level).reversed();

    public List<LineStatusDto> toDtos(List<TflLine> lines) {
        return lines.stream()
                .filter(line -> line.id() != null)
                .map(this::toDto)
                .sorted(Comparator.comparingInt((LineStatusDto l) -> modeRank(l.mode()))
                        .thenComparing(LineStatusDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    LineStatusDto toDto(TflLine line) {
        List<LineStatusEntryDto> entries = line.lineStatuses() == null ? List.of()
                : line.lineStatuses().stream()
                        .filter(Objects::nonNull)
                        .map(this::toEntry)
                        .distinct()
                        .sorted(MOST_SERIOUS_FIRST)
                        .toList();

        ServiceLevel level = entries.isEmpty() ? ServiceLevel.UNKNOWN : entries.getFirst().level();
        String summary = entries.isEmpty() || entries.getFirst().severityDescription() == null
                ? STATUS_UNAVAILABLE
                : entries.getFirst().severityDescription();

        String name = line.name() == null ? line.id() : line.name();
        return new LineStatusDto(line.id(), name, line.modeName(), level, summary, entries);
    }

    private LineStatusEntryDto toEntry(TflLine.LineStatus status) {
        // Prefer the validity period TfL marks as current; otherwise the first one listed.
        TflLine.ValidityPeriod period = status.validityPeriods() == null ? null
                : status.validityPeriods().stream()
                        .filter(Objects::nonNull)
                        .filter(p -> Boolean.TRUE.equals(p.isNow()))
                        .findFirst()
                        .orElse(status.validityPeriods().stream().filter(Objects::nonNull).findFirst().orElse(null));

        Instant from = period == null ? null : TflDateTimes.parse(period.fromDate()).orElse(null);
        Instant to = period == null ? null : TflDateTimes.parse(period.toDate()).orElse(null);
        String reason = status.reason() == null || status.reason().isBlank() ? null : status.reason().strip();

        return new LineStatusEntryDto(
                status.statusSeverity(),
                status.statusSeverityDescription(),
                ServiceLevel.fromTflSeverity(status.statusSeverity()),
                reason,
                from,
                to,
                ServiceLevel.isStepFreeAccessIssue(status.statusSeverity()));
    }

    private static int modeRank(String mode) {
        int index = MODE_ORDER.indexOf(mode);
        return index < 0 ? MODE_ORDER.size() : index;
    }
}
