package com.stepfreelondon.journey;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.config.ProblemDetailSecurityHandler;
import com.stepfreelondon.config.SecurityConfig;
import com.stepfreelondon.journey.dto.JourneyPlanDto;
import com.stepfreelondon.journey.dto.JourneySearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JourneyController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class})
class JourneyControllerTest {

    private static final LiveData<JourneyPlanDto> EMPTY =
            LiveData.fresh(new JourneyPlanDto(List.of(), List.of(), List.of(), List.of()), Instant.parse("2026-10-23T12:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JourneyService journeyService;

    @Test
    void bindsCommaSeparatedPreferencesAndOptions() throws Exception {
        given(journeyService.plan(any())).willReturn(EMPTY);

        mvc.perform(get("/api/v1/journeys")
                        .param("from", "1000257").param("to", "1000129")
                        .param("accessibility", "STEP_FREE_TO_PLATFORM,NO_ESCALATORS")
                        .param("preference", "LEAST_WALKING").param("walkingSpeed", "SLOW")
                        .param("time", "2026-10-24T08:30:00").param("arriveBy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journeys").isEmpty());

        ArgumentCaptor<JourneySearchRequest> captor = ArgumentCaptor.forClass(JourneySearchRequest.class);
        verify(journeyService).plan(captor.capture());
        JourneySearchRequest request = captor.getValue();
        assertThat(request.accessibility()).containsExactly(AccessibilityPreference.STEP_FREE_TO_PLATFORM, AccessibilityPreference.NO_ESCALATORS);
        assertThat(request.preference()).isEqualTo(JourneyPreference.LEAST_WALKING);
        assertThat(request.isArriveBy()).isTrue();
        assertThat(request.time()).hasToString("2026-10-24T08:30");
    }

    @Test
    void bindsRepeatedPreferenceParameters() throws Exception {
        given(journeyService.plan(any())).willReturn(EMPTY);

        mvc.perform(get("/api/v1/journeys").param("from", "1000257").param("to", "1000129")
                        .param("accessibility", "NO_SOLID_STAIRS").param("accessibility", "NO_ELEVATORS"))
                .andExpect(status().isOk());

        ArgumentCaptor<JourneySearchRequest> captor = ArgumentCaptor.forClass(JourneySearchRequest.class);
        verify(journeyService).plan(captor.capture());
        assertThat(captor.getValue().accessibility()).hasSize(2);
    }

    @Test
    void rejectsSameOriginAndDestination() throws Exception {
        mvc.perform(get("/api/v1/journeys").param("from", "1000257").param("to", "1000257"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("origin and destination must be different"));
        verifyNoInteractions(journeyService);
    }

    @Test
    void rejectsNonIcsIdentifiers() throws Exception {
        mvc.perform(get("/api/v1/journeys").param("from", "HUBWMB").param("to", "1000129"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        verifyNoInteractions(journeyService);
    }

    @Test
    void rejectsUnsupportedAccessibilityValuesWithoutLeakingConversionErrors() throws Exception {
        mvc.perform(get("/api/v1/journeys").param("from", "1000257").param("to", "1000129").param("accessibility", "FLYING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accessibility"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
        verifyNoInteractions(journeyService);
    }

    @Test
    void missingStationsAreValidationErrors() throws Exception {
        mvc.perform(get("/api/v1/journeys"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"));
    }

    @Test
    void noJourneyFoundIs404WithHelpfulDetail() throws Exception {
        given(journeyService.plan(any())).willThrow(new NoJourneyFoundException(true));

        mvc.perform(get("/api/v1/journeys").param("from", "1000257").param("to", "1000129"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/no-journey-found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("fewer options")));
    }
}
