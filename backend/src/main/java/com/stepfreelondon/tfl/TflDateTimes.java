package com.stepfreelondon.tfl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Parses TfL timestamps. TfL sends both UTC instants ({@code 2026-10-23T14:44:33Z}) and zone-less
 * London local times ({@code 2026-10-23T17:24:00}). It also uses {@code 0001-01-01T00:00:00} to
 * mean "no value".
 */
public final class TflDateTimes {

    public static final ZoneId LONDON = ZoneId.of("Europe/London");

    private TflDateTimes() {
    }

    public static Optional<Instant> parse(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-01-01")) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException notOffset) {
            try {
                return Optional.of(LocalDateTime.parse(value).atZone(LONDON).toInstant());
            } catch (DateTimeParseException unparseable) {
                return Optional.empty();
            }
        }
    }
}
