package com.stepfreelondon.monitoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;
import com.stepfreelondon.line.ServiceLevel;
import com.stepfreelondon.line.dto.LineStatusDto;
import com.stepfreelondon.line.dto.LineStatusEntryDto;

/**
 * Turns TfL feeds into {@link ObservedDisruption}s with an identity key and a content hash.
 *
 * <p>TfL supplies no disruption ids (feasibility.md §1.8), so identity is built from what stays
 * the same while a disruption is ongoing, and the hash from the text a user would read:
 * <ul>
 *   <li><b>Line status</b>: identity = line id. Severity and reasons are content, so "Minor Delays"
 *       becoming "Severe Delays" is an update to the same event, not a new one.</li>
 *   <li><b>Station notices</b>: TfL repeats one notice at every station it concerns (141 rows, 62
 *       distinct texts on 2026-10-24). Rows are grouped into one event; identity = type + start time
 *       + the notice heading ("CIRCLE LINE", "WEMBLEY PARK STATION") + concerned lines. A heading-less
 *       notice falls back to its text, so editing it reads as resolved + new.</li>
 *   <li><b>Lifts</b>: identity = station id; the lift ids and message are content.</li>
 * </ul>
 */
@Component
class DisruptionNormaliser {

    /** A notice heading is the upper-case text before the first colon, e.g. "BARONS COURT STATION:". */
    private static final Pattern HEADING = Pattern.compile("^([^:\\n]{3,60}):");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Rail line ids from {@code /Line/Mode/{modes}/Status} (checked 2026-10-24). */
    static final Set<String> KNOWN_LINES = Set.of("bakerloo", "central", "circle", "district", "dlr", "elizabeth",
            "hammersmith-city", "jubilee", "liberty", "lioness", "metropolitan", "mildmay", "northern", "piccadilly",
            "suffragette", "tram", "victoria", "waterloo-city", "weaver", "windrush");
    private static final Set<ServiceLevel> DISRUPTED =
            Set.of(ServiceLevel.MINOR_DISRUPTION, ServiceLevel.SEVERE_DISRUPTION, ServiceLevel.CLOSED);

    /** Lines at minor disruption or worse. Good service, information-only and unknown statuses create no event. */
    List<ObservedDisruption> fromLineStatuses(List<LineStatusDto> lines) {
        return lines.stream()
                .filter(line -> DISRUPTED.contains(line.level()))
                .map(DisruptionNormaliser::fromLine)
                .toList();
    }

    private static ObservedDisruption fromLine(LineStatusDto line) {
        List<LineStatusEntryDto> disrupted = line.statuses().stream().filter(s -> DISRUPTED.contains(s.level())).toList();
        LineStatusEntryDto worst = disrupted.getFirst(); // statuses are ordered most serious first
        List<String> reasons = disrupted.stream().map(LineStatusEntryDto::reason).filter(Objects::nonNull)
                .map(DisruptionNormaliser::normalise).distinct().toList();
        String description = reasons.isEmpty() ? line.name() + ": " + line.summary() : String.join("\n\n", reasons);
        String content = disrupted.stream()
                .map(s -> s.severityCode() + "|" + normalise(Objects.toString(s.reason(), "")))
                .sorted().collect(Collectors.joining("\n"));
        return new ObservedDisruption(DisruptionSource.LINE_STATUS, line.id(), sha256(content),
                line.mode(), worst.severityDescription(), worst.severityCode(), worst.severityDescription(),
                line.name() + ": " + line.summary(), description, worst.validFrom(), worst.validTo(),
                Set.of(line.id()), Set.of());
    }

    List<ObservedDisruption> fromStationDisruptions(List<StationDisruptionDto> disruptions) {
        Map<String, List<StationDisruptionDto>> groups = new LinkedHashMap<>();
        for (StationDisruptionDto d : disruptions) {
            if (d.stationId() != null && d.description() != null && !d.description().isBlank()) {
                groups.computeIfAbsent(stationIdentity(d), k -> new ArrayList<>()).add(d);
            }
        }
        return groups.entrySet().stream().map(e -> fromStationGroup(e.getKey(), e.getValue())).toList();
    }

    static String stationIdentity(StationDisruptionDto d) {
        String heading = heading(d.description());
        String subject = heading != null ? heading : "text:" + sha256(normalise(d.description()));
        return String.join("|", Objects.toString(d.type(), ""), Objects.toString(d.validFrom(), ""), subject,
                String.join(",", new TreeSet<>(d.lineIds())));
    }

    private static ObservedDisruption fromStationGroup(String identity, List<StationDisruptionDto> group) {
        // TfL sends the same text for every station in the group; sort so the choice never depends on feed order.
        List<StationDisruptionDto> sorted = group.stream()
                .sorted(Comparator.comparing(StationDisruptionDto::description).thenComparing(StationDisruptionDto::stationId))
                .toList();
        StationDisruptionDto first = sorted.getFirst();
        Set<String> stations = sorted.stream().map(StationDisruptionDto::stationId).collect(Collectors.toSet());
        Set<String> texts = sorted.stream().map(d -> normalise(d.description())).collect(Collectors.toCollection(TreeSet::new));
        Instant validTo = sorted.stream().map(StationDisruptionDto::validTo).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);

        String type = first.type() == null ? "Station disruption" : first.type();
        String title = stations.size() == 1 && first.stationName() != null ? type + " at " + first.stationName() : type;
        String content = String.join("\n", texts) + "|" + Objects.toString(validTo, "") + "|" + Objects.toString(first.appearance(), "");
        return new ObservedDisruption(DisruptionSource.STOP_POINT, identity, sha256(content),
                first.appearance(), first.type(), null, null, title, normalise(first.description()),
                first.validFrom(), validTo, linesOf(first), stations);
    }

    /**
     * TfL's concerned lines, or, when it lists none, the line named in the heading: "CIRCLE LINE: no
     * service…" arrives at Victoria with no concerned lines, and must not match a Victoria line journey.
     * An unrecognised heading leaves the set empty, which matches on the station alone (the safe side).
     */
    static Set<String> linesOf(StationDisruptionDto d) {
        if (!d.lineIds().isEmpty()) {
            return Set.copyOf(d.lineIds());
        }
        String heading = heading(d.description());
        if (heading == null || !heading.endsWith(" LINE")) {
            return Set.of();
        }
        String id = heading.substring(0, heading.length() - " LINE".length()).toLowerCase(Locale.ROOT)
                .replace("&", " ").strip().replaceAll("\\s+", "-");
        return KNOWN_LINES.contains(id) ? Set.of(id) : Set.of();
    }

    List<ObservedDisruption> fromLiftOutages(List<LiftOutageDto> outages) {
        Map<String, List<LiftOutageDto>> byStation = outages.stream()
                .filter(o -> o.stationId() != null)
                .collect(Collectors.groupingBy(LiftOutageDto::stationId, LinkedHashMap::new, Collectors.toList()));
        return byStation.entrySet().stream().map(e -> {
            Set<String> liftIds = e.getValue().stream().flatMap(o -> o.liftIds().stream()).collect(Collectors.toCollection(TreeSet::new));
            String message = e.getValue().stream().map(LiftOutageDto::message).filter(Objects::nonNull)
                    .map(DisruptionNormaliser::normalise).distinct().sorted().collect(Collectors.joining("\n\n"));
            String description = message.isEmpty() ? "TfL reports a lift out of service at this station." : message;
            String title = liftIds.size() > 1 ? "Lifts out of service" : "Lift out of service";
            return new ObservedDisruption(DisruptionSource.LIFT, e.getKey(), sha256(String.join(",", liftIds) + "|" + message),
                    "lift", "Lift outage", null, null, title, description, null, null, Set.of(), Set.of(e.getKey()));
        }).toList();
    }

    static String heading(String description) {
        var matcher = HEADING.matcher(description.strip());
        if (!matcher.find()) {
            return null;
        }
        String heading = normalise(matcher.group(1)).toUpperCase(Locale.ROOT);
        return heading.isBlank() ? null : heading;
    }

    static String normalise(String text) {
        return WHITESPACE.matcher(text.strip()).replaceAll(" ");
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
