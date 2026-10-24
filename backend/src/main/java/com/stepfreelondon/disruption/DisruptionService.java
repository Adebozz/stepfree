package com.stepfreelondon.disruption;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCache;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.disruption.dto.DisruptionsOverviewDto;
import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;
import com.stepfreelondon.tfl.TflClient;

@Service
public class DisruptionService {

    static final List<String> MODES = List.of("tube", "elizabeth-line", "overground", "dlr", "tram");
    private static final String ALL = "all";

    private final TflClient tflClient;
    private final DisruptionMapper mapper;
    private final LiftOutageService liftOutageService;
    private final LiveDataCache<String, List<StationDisruptionDto>> stationCache;

    DisruptionService(TflClient tflClient, DisruptionMapper mapper, LiftOutageService liftOutageService,
                      LiveDataCacheFactory caches) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.liftOutageService = liftOutageService;
        this.stationCache = caches.create("station-disruptions", caches.properties().disruptionTtl(), 1);
    }

    /**
     * Station disruptions are required (failure propagates, or stale data is served); lift outages are
     * best-effort. The envelope reports the older of the two retrieval times and is stale if either is.
     */
    public LiveData<DisruptionsOverviewDto> overview() {
        LiveData<List<StationDisruptionDto>> stations =
                stationCache.get(ALL, key -> mapper.toStationDisruptions(tflClient.stopPointDisruptionsByModes(MODES)));
        Optional<LiveData<List<LiftOutageDto>>> lifts = liftOutageService.currentOutages();

        var overview = new DisruptionsOverviewDto(
                stations.data(),
                lifts.map(LiveData::data).orElse(List.of()),
                lifts.isPresent());
        var retrievedAt = lifts.map(LiveData::retrievedAt)
                .filter(liftTime -> liftTime.isBefore(stations.retrievedAt()))
                .orElse(stations.retrievedAt());
        boolean stale = stations.stale() || lifts.map(LiveData::stale).orElse(false);
        return new LiveData<>(overview, retrievedAt, stale);
    }
}
