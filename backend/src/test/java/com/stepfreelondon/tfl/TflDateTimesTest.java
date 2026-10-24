package com.stepfreelondon.tfl;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TflDateTimesTest {

    @Test
    void parsesUtcTimestamps() {
        assertThat(TflDateTimes.parse("2026-10-23T14:44:33Z")).contains(Instant.parse("2026-10-23T14:44:33Z"));
    }

    @Test
    void treatsZonelessTimestampsAsLondonLocalTime() {
        // 23 Oct 2026 is British Summer Time (UTC+1).
        assertThat(TflDateTimes.parse("2026-10-23T17:24:00")).contains(Instant.parse("2026-10-23T16:24:00Z"));
        // 1 Dec 2026 is GMT (UTC+0).
        assertThat(TflDateTimes.parse("2026-12-01T09:00:00")).contains(Instant.parse("2026-12-01T09:00:00Z"));
    }

    @Test
    void treatsTflPlaceholderAndGarbageAsAbsent() {
        assertThat(TflDateTimes.parse("0001-01-01T00:00:00")).isEmpty();
        assertThat(TflDateTimes.parse("not a date")).isEmpty();
        assertThat(TflDateTimes.parse("")).isEmpty();
        assertThat(TflDateTimes.parse(null)).isEmpty();
    }
}
