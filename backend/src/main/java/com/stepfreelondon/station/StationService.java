package com.stepfreelondon.station;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCache;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.station.dto.StationSummaryDto;
import com.stepfreelondon.tfl.TflClient;

@Service
public class StationService {

    static final int MAX_RESULTS = 15;

    private final TflClient tflClient;
    private final StationMapper mapper;
    private final LiveDataCache<String, List<StationSummaryDto>> searchCache;

    StationService(TflClient tflClient, StationMapper mapper, LiveDataCacheFactory caches) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.searchCache = caches.create("station-search", caches.properties().stationSearchTtl(), 2_000);
    }

    /**
     * Searches stations by (partial) name. The query is normalised before caching so that
     * "Wembley", " wembley " and "WEMBLEY" share one cache entry and one TfL call.
     */
    public LiveData<List<StationSummaryDto>> search(String query) {
        String normalised = normalise(query);
        return searchCache.get(normalised,
                q -> mapper.toSummaries(tflClient.searchStopPoints(q, StationSearchModes.RAIL, MAX_RESULTS)));
    }

    static String normalise(String query) {
        return query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.UK);
    }
}
