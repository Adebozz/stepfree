package com.stepfreelondon.common.live;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.stepfreelondon.tfl.TflNotFoundException;
import com.stepfreelondon.tfl.TflRateLimitedException;
import com.stepfreelondon.tfl.TflUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveDataCacheTest {

    private static final Instant START = Instant.parse("2026-10-23T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final LiveDataCache<String, String> cache = new LiveDataCache<>(
            "test", Duration.ofSeconds(60), Duration.ofHours(6), 100, clock,
            () -> TimeUnit.MILLISECONDS.toNanos(clock.millis()));
    private final AtomicInteger loads = new AtomicInteger();

    private String load(String key) {
        return key + "-v" + loads.incrementAndGet();
    }

    @Test
    void servesCachedValueWithinTtlWithoutCallingTfl() {
        LiveData<String> first = cache.get("k", this::load);
        clock.advance(Duration.ofSeconds(59));
        LiveData<String> second = cache.get("k", this::load);

        assertThat(second).isEqualTo(first);
        assertThat(second.stale()).isFalse();
        assertThat(second.retrievedAt()).isEqualTo(START);
        assertThat(loads).hasValue(1);
    }

    @Test
    void reloadsAfterTtlAndUpdatesRetrievedAt() {
        cache.get("k", this::load);
        clock.advance(Duration.ofSeconds(61));

        LiveData<String> reloaded = cache.get("k", this::load);

        assertThat(reloaded.data()).isEqualTo("k-v2");
        assertThat(reloaded.retrievedAt()).isEqualTo(START.plusSeconds(61));
    }

    @Test
    void fallsBackToLastGoodValueMarkedStaleWhenTflIsUnavailable() {
        cache.get("k", this::load);
        clock.advance(Duration.ofMinutes(5));

        LiveData<String> result = cache.get("k", key -> { throw new TflUnavailableException("test", "down"); });

        assertThat(result.data()).isEqualTo("k-v1");
        assertThat(result.stale()).isTrue();
        assertThat(result.retrievedAt()).as("keeps the original retrieval time").isEqualTo(START);
    }

    @Test
    void fallsBackWhenRateLimited() {
        cache.get("k", this::load);
        clock.advance(Duration.ofMinutes(2));

        LiveData<String> result = cache.get("k", key -> { throw new TflRateLimitedException("test", null); });

        assertThat(result.stale()).isTrue();
    }

    @Test
    void propagatesFailureWhenNothingWasEverCached() {
        assertThatThrownBy(() -> cache.get("k", key -> { throw new TflUnavailableException("test", "down"); }))
                .isInstanceOf(TflUnavailableException.class);
    }

    @Test
    void doesNotHideNonTransientErrorsBehindStaleData() {
        cache.get("k", this::load);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> cache.get("k", key -> { throw new TflNotFoundException("test", "gone"); }))
                .isInstanceOf(TflNotFoundException.class);
    }

    @Test
    void doesNotServeDataOlderThanStaleMaxAge() {
        cache.get("k", this::load);
        clock.advance(Duration.ofHours(7));

        assertThatThrownBy(() -> cache.get("k", key -> { throw new TflUnavailableException("test", "down"); }))
                .isInstanceOf(TflUnavailableException.class);
    }

    @Test
    void keysAreIndependent() {
        cache.get("a", this::load);
        LiveData<String> b = cache.get("b", this::load);

        assertThat(b.data()).isEqualTo("b-v2");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
