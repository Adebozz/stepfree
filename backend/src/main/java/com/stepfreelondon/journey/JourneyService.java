package com.stepfreelondon.journey;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.stepfreelondon.common.error.RequestValidationException;
import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.common.live.LiveDataCache;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.journey.dto.JourneyPlanDto;
import com.stepfreelondon.journey.dto.JourneySearchRequest;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflDateTimes;
import com.stepfreelondon.tfl.TflJourneyQuery;
import com.stepfreelondon.tfl.TflNotFoundException;

@Service
public class JourneyService {

    /** TfL rejects dates over 7 days in the past and errors ~3 months ahead (both verified 2026-10-23). */
    static final Duration MAX_PAST = Duration.ofHours(1);
    static final Duration MAX_FUTURE = Duration.ofDays(28);

    private final TflClient tflClient;
    private final JourneyMapper mapper;
    private final Clock clock;
    private final LiveDataCache<TflJourneyQuery, JourneyPlanDto> cache;

    JourneyService(TflClient tflClient, JourneyMapper mapper, Clock clock, LiveDataCacheFactory caches) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.clock = clock;
        this.cache = caches.create("journey-results", caches.properties().journeyTtl(), 1_000);
    }

    public LiveData<JourneyPlanDto> plan(JourneySearchRequest request) {
        validateTime(request.time());
        TflJourneyQuery query = toQuery(request);
        try {
            return cache.get(query, q -> mapper.toPlan(tflClient.journeyResults(q), request.accessibility()));
        } catch (TflNotFoundException e) {
            throw new NoJourneyFoundException(!request.accessibility().isEmpty());
        }
    }

    static TflJourneyQuery toQuery(JourneySearchRequest request) {
        return new TflJourneyQuery(
                request.from(),
                request.to(),
                request.accessibility().stream().map(AccessibilityPreference::tflValue).toList(),
                request.preference() == null ? null : request.preference().tflValue(),
                request.walkingSpeed() == null ? null : request.walkingSpeed().tflValue(),
                request.time(),
                request.isArriveBy());
    }

    private void validateTime(LocalDateTime time) {
        if (time == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(TflDateTimes.LONDON));
        if (time.isBefore(now.minus(MAX_PAST))) {
            throw new RequestValidationException("time", "must not be in the past");
        }
        if (time.isAfter(now.plus(MAX_FUTURE))) {
            throw new RequestValidationException("time", "must be within the next 28 days");
        }
    }
}
