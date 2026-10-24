package com.stepfreelondon.disruption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.disruption.dto.DisruptionsOverviewDto;

@Tag(name = "Disruptions")
@RestController
@RequestMapping("/api/v1/disruptions")
class DisruptionController {

    private final DisruptionService disruptionService;

    DisruptionController(DisruptionService disruptionService) {
        this.disruptionService = disruptionService;
    }

    @Operation(summary = "Current station disruptions and reported lift outages",
            description = "Line-level disruptions are available from /api/v1/lines/status.")
    @GetMapping
    LiveData<DisruptionsOverviewDto> overview() {
        return disruptionService.overview();
    }
}
