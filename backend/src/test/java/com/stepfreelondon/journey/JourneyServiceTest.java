package com.stepfreelondon.journey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import com.stepfreelondon.common.error.RequestValidationException;
import com.stepfreelondon.common.live.LiveCacheProperties;
import com.stepfreelondon.common.live.LiveDataCacheFactory;
import com.stepfreelondon.journey.dto.JourneySearchRequest;
import com.stepfreelondon.line.LineStatusMapper;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.TflJourneyQuery;
import com.stepfreelondon.tfl.TflNotFoundException;
import com.stepfreelondon.tfl.model.TflJourneyResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JourneyServiceTest {

    /** 12:00 UTC = 13:00 in London (BST). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime LONDON_NOW = LocalDateTime.parse("2026-10-23T13:00:00");

    private final TflClient tflClient = mock(TflClient.class);
    private final JourneyService service = new JourneyService(
            tflClient,
            new JourneyMapper(TflFixtures.JSON, new LineStatusMapper()),
            CLOCK,
            new LiveDataCacheFactory(CLOCK, new SimpleMeterRegistry(), new LiveCacheProperties(
                    Duration.ofHours(1), Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofHours(1),
                    Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofHours(6))));

    private static JourneySearchRequest request(List<AccessibilityPreference> accessibility, LocalDateTime time) {
        return new JourneySearchRequest("1000257", "1000129", accessibility, null, null, time, null);
    }

    @Test
    void translatesPreferencesToExactTflValues() {
        JourneySearchRequest request = new JourneySearchRequest("1000257", "1000129",
                List.of(AccessibilityPreference.NO_ESCALATORS, AccessibilityPreference.STEP_FREE_TO_PLATFORM, AccessibilityPreference.NO_ESCALATORS),
                JourneyPreference.LEAST_WALKING, WalkingSpeed.SLOW, LONDON_NOW.plusHours(2), true);

        TflJourneyQuery query = JourneyService.toQuery(request);

        assertThat(query.accessibilityPreferences()).as("de-duplicated and in a stable order")
                .containsExactly("StepFreeToPlatform", "NoEscalators");
        assertThat(query.journeyPreference()).isEqualTo("LeastWalking");
        assertThat(query.walkingSpeed()).isEqualTo("Slow");
        assertThat(query.arriveBy()).isTrue();
    }

    @Test
    void identicalSearchesShareOneTflCall() {
        given(tflClient.journeyResults(any())).willReturn(
                TflFixtures.read("journey-wembley-park-to-kings-cross-step-free.json", TflJourneyResponse.class));

        service.plan(request(List.of(AccessibilityPreference.STEP_FREE_TO_PLATFORM), null));
        var second = service.plan(request(List.of(AccessibilityPreference.STEP_FREE_TO_PLATFORM), null));

        assertThat(second.data().journeys()).hasSize(3);
        verify(tflClient, times(1)).journeyResults(any());
    }

    @Test
    void noJourneyFromTflBecomesDomainError() {
        given(tflClient.journeyResults(any())).willThrow(new TflNotFoundException("journey-results", "none"));

        assertThatThrownBy(() -> service.plan(request(List.of(AccessibilityPreference.STEP_FREE_TO_PLATFORM), null)))
                .isInstanceOf(NoJourneyFoundException.class)
                .hasMessageContaining("accessibility options");
    }

    @Test
    void rejectsTimesInThePastBeforeCallingTfl() {
        assertThatThrownBy(() -> service.plan(request(List.of(), LONDON_NOW.minusHours(2))))
                .isInstanceOfSatisfying(RequestValidationException.class, e -> assertThat(e.field()).isEqualTo("time"));
        verifyNoInteractions(tflClient);
    }

    @Test
    void rejectsTimesBeyondTflsPlanningHorizon() {
        assertThatThrownBy(() -> service.plan(request(List.of(), LONDON_NOW.plusDays(29))))
                .isInstanceOf(RequestValidationException.class)
                .hasMessageContaining("28 days");
        verifyNoInteractions(tflClient);
    }

    @Test
    void allowsSlightlyPastTimesSoLeaveNowStillWorks() {
        given(tflClient.journeyResults(any())).willReturn(new TflJourneyResponse(List.of(), List.of(), List.of()));

        assertThat(service.plan(request(List.of(), LONDON_NOW.minusMinutes(30))).data().journeys()).isEmpty();
    }
}
