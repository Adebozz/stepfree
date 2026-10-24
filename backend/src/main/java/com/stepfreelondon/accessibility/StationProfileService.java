package com.stepfreelondon.accessibility;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.stepfreelondon.accessibility.dto.StationAccessibilityDto;
import com.stepfreelondon.accessibility.dto.StationDetailDto;
import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCache;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.disruption.DisruptionMapper;
import com.stepfreelondon.disruption.LiftOutageService;
import com.stepfreelondon.disruption.dto.LiftOutageDto;
import com.stepfreelondon.disruption.dto.StationDisruptionDto;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflException;

@Service
public class StationProfileService {

    private static final Logger log = LoggerFactory.getLogger(StationProfileService.class);

    private final TflClient tflClient;
    private final StationProfileMapper profileMapper;
    private final DisruptionMapper disruptionMapper;
    private final LiftOutageService liftOutageService;
    private final Clock clock;
    private final LiveDataCache<String, StationProfile> profileCache;
    private final LiveDataCache<String, List<StationDisruptionDto>> disruptionCache;

    StationProfileService(TflClient tflClient, StationProfileMapper profileMapper, DisruptionMapper disruptionMapper,
                          LiftOutageService liftOutageService, Clock clock, LiveDataCacheFactory caches) {
        this.tflClient = tflClient;
        this.profileMapper = profileMapper;
        this.disruptionMapper = disruptionMapper;
        this.liftOutageService = liftOutageService;
        this.clock = clock;
        this.profileCache = caches.create("station-profile", caches.properties().stationInfoTtl(), 2_000);
        this.disruptionCache = caches.create("station-profile-disruptions", caches.properties().disruptionTtl(), 2_000);
    }

    public LiveData<StationDetailDto> detail(String stationId) {
        LiveData<StationProfile> profile = profile(stationId);
        return new LiveData<>(profile.data().detail(), profile.retrievedAt(), profile.stale());
    }

    /**
     * Combines static station data (required) with live disruptions and lift outages (each optional).
     * If a live source fails, its "available" flag is false: the UI must not read an empty list as
     * "no problems".
     */
    public LiveData<StationAccessibilityDto> accessibility(String stationId) {
        LiveData<StationProfile> profile = profile(stationId);
        StationProfile station = profile.data();
        Set<String> memberIds = Set.copyOf(Stream.concat(station.memberIds().stream(), Stream.of(station.detail().id())).toList());

        Optional<LiveData<List<StationDisruptionDto>>> disruptions = stationDisruptions(stationId, station.memberIds());
        Optional<LiveData<List<LiftOutageDto>>> lifts = liftOutageService.currentOutages();

        List<LiftOutageDto> stationLifts = lifts.map(l -> l.data().stream().filter(o -> memberIds.contains(o.stationId())).toList())
                .orElse(List.of());

        var dto = new StationAccessibilityDto(
                station.detail().id(),
                station.detail().name(),
                station.detail().hub(),
                station.sections(),
                stationLifts,
                lifts.isPresent(),
                withoutLiftOutages(disruptions.map(LiveData::data).orElse(List.of()), stationLifts),
                disruptions.isPresent(),
                profile.retrievedAt());

        // The envelope describes the live parts; static data freshness is in stationInfoRetrievedAt.
        Instant retrievedAt = Stream.of(disruptions, lifts)
                .flatMap(Optional::stream)
                .map(LiveData::retrievedAt)
                .min(Instant::compareTo)
                .orElse(profile.retrievedAt());
        boolean stale = profile.stale()
                || disruptions.map(LiveData::stale).orElse(false)
                || lifts.map(LiveData::stale).orElse(false);
        return new LiveData<>(dto, retrievedAt, stale);
    }

    private LiveData<StationProfile> profile(String stationId) {
        return profileCache.get(stationId, id -> profileMapper.toProfile(tflClient.stopPoint(id)));
    }

    private Optional<LiveData<List<StationDisruptionDto>>> stationDisruptions(String stationId, List<String> memberIds) {
        if (memberIds.isEmpty()) {
            return Optional.of(LiveData.fresh(List.of(), clock.instant()));
        }
        try {
            return Optional.of(disruptionCache.get(stationId,
                    id -> distinctByDescription(disruptionMapper.toStationDisruptions(tflClient.stopPointDisruptions(memberIds)))));
        } catch (TflException e) {
            log.warn("Station disruptions unavailable for {}: {}", stationId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * TfL often publishes a lift notice both as a lift disruption and as a station disruption with
     * identical text. It is already shown under lift outages, so drop the repeat.
     */
    static List<StationDisruptionDto> withoutLiftOutages(List<StationDisruptionDto> disruptions, List<LiftOutageDto> outages) {
        Set<String> liftMessages = outages.stream().map(o -> normalise(o.message())).collect(java.util.stream.Collectors.toSet());
        return disruptions.stream().filter(d -> !liftMessages.contains(normalise(d.description()))).toList();
    }

    private static String normalise(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    /** TfL repeats a hub-wide notice once per member station; show it once. */
    static List<StationDisruptionDto> distinctByDescription(List<StationDisruptionDto> disruptions) {
        Set<String> seen = new java.util.HashSet<>();
        return disruptions.stream().filter(d -> seen.add(d.description())).toList();
    }
}
