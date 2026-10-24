package com.stepfreelondon.disruption;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCache;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflException;

/**
 * Reported lift outages. Backed by an undocumented TfL endpoint, so failure is an expected state:
 * callers get {@link Optional#empty()} and must show "lift status unavailable".
 */
@Service
public class LiftOutageService {

    private static final Logger log = LoggerFactory.getLogger(LiftOutageService.class);
    private static final String ALL = "all";

    private final TflClient tflClient;
    private final DisruptionMapper mapper;
    private final LiveDataCache<String, List<LiftOutageDto>> cache;

    LiftOutageService(TflClient tflClient, DisruptionMapper mapper, LiveDataCacheFactory caches) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.cache = caches.create("lift-outages", caches.properties().liftStatusTtl(), 1);
    }

    public Optional<LiveData<List<LiftOutageDto>>> currentOutages() {
        try {
            return Optional.of(cache.get(ALL, key -> mapper.toLiftOutages(tflClient.liftDisruptions())));
        } catch (TflException e) {
            log.warn("Lift outage data unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
