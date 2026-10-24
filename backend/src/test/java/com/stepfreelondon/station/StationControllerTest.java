package com.stepfreelondon.station;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.config.ProblemDetailSecurityHandler;
import com.stepfreelondon.config.SecurityConfig;
import com.stepfreelondon.station.dto.StationSummaryDto;
import com.stepfreelondon.tfl.TflRateLimitedException;
import com.stepfreelondon.tfl.TflUnavailableException;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StationController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class})
class StationControllerTest {

    private static final Instant RETRIEVED = Instant.parse("2026-10-23T16:24:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StationService stationService;

    @Test
    void returnsMatchingStationsWithFreshnessMetadata() throws Exception {
        var park = new StationSummaryDto("940GZZLUWYP", "1000257", "Wembley Park", List.of("tube"), "4", 51.56, -0.28, false);
        given(stationService.search("Wembley")).willReturn(LiveData.fresh(List.of(park), RETRIEVED));

        mvc.perform(get("/api/v1/stations").param("query", "Wembley"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(jsonPath("$.data[0].id").value("940GZZLUWYP"))
                .andExpect(jsonPath("$.data[0].icsCode").value("1000257"))
                .andExpect(jsonPath("$.data[0].name").value("Wembley Park"))
                .andExpect(jsonPath("$.data[0].hub").value(false))
                .andExpect(jsonPath("$.retrievedAt").value("2026-10-23T16:24:00Z"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void noMatchesIsAnEmptyListNotAnError() throws Exception {
        given(stationService.search(anyString())).willReturn(LiveData.fresh(List.of(), RETRIEVED));

        mvc.perform(get("/api/v1/stations").param("query", "zzqqxx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsTooShortQueryWithProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/stations").param("query", "W").header("X-Correlation-Id", "test-correlation-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("query"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-1"));
        verifyNoInteractions(stationService);
    }

    @Test
    void rejectsUnsafeCharacters() throws Exception {
        mvc.perform(get("/api/v1/stations").param("query", "<script>alert(1)</script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("query"));
        verifyNoInteractions(stationService);
    }

    @Test
    void acceptsRealStationNamesWithPunctuation() throws Exception {
        given(stationService.search(anyString())).willReturn(LiveData.fresh(List.of(), RETRIEVED));

        mvc.perform(get("/api/v1/stations").param("query", "King's Cross St. Pancras"))
                .andExpect(status().isOk());
    }

    @Test
    void missingQueryIsAProblemDetailWithCorrelationId() throws Exception {
        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void tflOutageBecomes503WithoutLeakingInternals() throws Exception {
        given(stationService.search(anyString())).willThrow(new TflUnavailableException("stop-point-search", "HTTP 502"));

        mvc.perform(get("/api/v1/stations").param("query", "Wembley"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("/problems/upstream-unavailable"))
                .andExpect(jsonPath("$.detail").value("Transport for London's data service could not be reached. Please try again shortly."));
    }

    @Test
    void tflRateLimitBecomes503WithRetryAfter() throws Exception {
        given(stationService.search(anyString())).willThrow(new TflRateLimitedException("stop-point-search", Duration.ofSeconds(20)));

        mvc.perform(get("/api/v1/stations").param("query", "Wembley"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "20"))
                .andExpect(jsonPath("$.type").value("/problems/upstream-rate-limited"));
    }

    @Test
    void unexpectedErrorsAreGeneric500s() throws Exception {
        given(stationService.search(anyString())).willThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/api/v1/stations").param("query", "Wembley"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("/problems/internal-error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internal detail"))));
    }

    @Test
    void protectedApiRoutesReturn401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/saved-journeys"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/unauthenticated"))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
