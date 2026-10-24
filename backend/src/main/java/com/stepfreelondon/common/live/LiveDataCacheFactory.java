package com.stepfreelondon.common.live;

import java.time.Clock;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Creates {@link LiveDataCache}s that share the clock, stale window and metrics registry. */
@Component
@EnableConfigurationProperties(LiveCacheProperties.class)
public class LiveDataCacheFactory {

    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final LiveCacheProperties properties;

    public LiveDataCacheFactory(Clock clock, MeterRegistry meterRegistry, LiveCacheProperties properties) {
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public <K, V> LiveDataCache<K, V> create(String name, Duration ttl, long maxSize) {
        LiveDataCache<K, V> cache = new LiveDataCache<>(name, ttl, properties.staleMaxAge(), maxSize, clock, Ticker.systemTicker());
        cache.bindMetrics(meterRegistry);
        return cache;
    }

    public LiveCacheProperties properties() {
        return properties;
    }
}
