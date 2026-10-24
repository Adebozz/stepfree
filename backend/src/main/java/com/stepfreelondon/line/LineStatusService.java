package com.stepfreelondon.line;

import java.util.List;

import org.springframework.stereotype.Service;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCache;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.line.dto.LineStatusDto;
import com.stepfreelondon.tfl.TflClient;

@Service
public class LineStatusService {

    /** Modes shown on the status board. London Overground is reported by TfL as six named lines. */
    static final List<String> MODES = List.of("tube", "elizabeth-line", "overground", "dlr", "tram");

    private static final String ALL_LINES = "all";

    private final TflClient tflClient;
    private final LineStatusMapper mapper;
    private final LiveDataCache<String, List<LineStatusDto>> cache;

    LineStatusService(TflClient tflClient, LineStatusMapper mapper, LiveDataCacheFactory caches) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.cache = caches.create("line-status", caches.properties().lineStatusTtl(), 1);
    }

    public LiveData<List<LineStatusDto>> currentStatus() {
        return cache.get(ALL_LINES, key -> mapper.toDtos(tflClient.lineStatusByModes(MODES)));
    }
}
