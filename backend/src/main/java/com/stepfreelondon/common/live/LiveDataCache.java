package com.stepfreelondon.common.live;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stepfreelondon.tfl.TflTransientException;

/**
 * A read-through cache for TfL data with an explicit stale fallback.
 *
 * <p>Two Caffeine caches are kept per name:
 * <ul>
 *   <li>{@code fresh}: entries live for the configured TTL; hits are returned as current data.</li>
 *   <li>{@code lastKnownGood}: the latest successful value, kept for {@code staleMaxAge}. It is only
 *       read when TfL fails with a transient error, and the result is marked {@code stale}.</li>
 * </ul>
 * Caffeine's {@code get(key, loader)} loads each key at most once concurrently, so a burst of
 * requests after expiry results in a single TfL call.
 *
 * <p>Spring's {@code @Cacheable} is not used because it cannot express "serve the old value, but
 * label it as old, only when the source is down".
 */
public final class LiveDataCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(LiveDataCache.class);

    private final String name;
    private final Cache<K, LiveData<V>> fresh;
    private final Cache<K, LiveData<V>> lastKnownGood;
    private final Clock clock;

    LiveDataCache(String name, Duration ttl, Duration staleMaxAge, long maxSize, Clock clock, Ticker ticker) {
        this.name = name;
        this.clock = clock;
        this.fresh = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).ticker(ticker).recordStats().build();
        this.lastKnownGood = Caffeine.newBuilder().expireAfterWrite(staleMaxAge).maximumSize(maxSize).ticker(ticker).build();
    }

    void bindMetrics(MeterRegistry registry) {
        CaffeineCacheMetrics.monitor(registry, fresh, name);
    }

    /**
     * Returns the cached value or loads it. On a transient TfL failure, returns the last good value
     * marked stale; if there is none, the failure propagates. Other exceptions always propagate.
     */
    public LiveData<V> get(K key, Function<K, V> loader) {
        try {
            return fresh.get(key, k -> {
                LiveData<V> loaded = LiveData.fresh(loader.apply(k), clock.instant());
                lastKnownGood.put(k, loaded);
                return loaded;
            });
        } catch (TflTransientException e) {
            LiveData<V> previous = lastKnownGood.getIfPresent(key);
            if (previous == null) {
                throw e;
            }
            log.warn("Serving stale '{}' data retrieved at {} because TfL failed: {}", name, previous.retrievedAt(), e.getMessage());
            return previous.asStale();
        }
    }
}
