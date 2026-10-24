package com.stepfreelondon.line;

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
import com.stepfreelondon.line.dto.LineStatusDto;
import com.stepfreelondon.line.dto.LineStatusEntryDto;
import com.stepfreelondon.tfl.TflUnavailableException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LineStatusController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class})
class LineStatusControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LineStatusService lineStatusService;

    @Test
    void returnsLineStatusesAndSurfacesStaleFlag() throws Exception {
        var entry = new LineStatusEntryDto(9, "Minor Delays", ServiceLevel.MINOR_DISRUPTION, "Train cancellations",
                Instant.parse("2026-10-23T14:44:33Z"), null, false);
        var central = new LineStatusDto("central", "Central", "tube", ServiceLevel.MINOR_DISRUPTION, "Minor Delays", List.of(entry));
        given(lineStatusService.currentStatus())
                .willReturn(LiveData.fresh(List.of(central), Instant.parse("2026-10-23T16:00:00Z")).asStale());

        mvc.perform(get("/api/v1/lines/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.retrievedAt").value("2026-10-23T16:00:00Z"))
                .andExpect(jsonPath("$.data[0].level").value("MINOR_DISRUPTION"))
                .andExpect(jsonPath("$.data[0].statuses[0].reason").value("Train cancellations"))
                .andExpect(jsonPath("$.data[0].statuses[0].stepFreeAccessAffected").value(false));
    }

    @Test
    void outageWithNoCachedDataIs503NotGoodService() throws Exception {
        given(lineStatusService.currentStatus()).willThrow(new TflUnavailableException("line-status", "down"));

        mvc.perform(get("/api/v1/lines/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("/problems/upstream-unavailable"));
    }
}
