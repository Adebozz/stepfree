package com.stepfreelondon.journey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.journey.dto.JourneyPlanDto;
import com.stepfreelondon.journey.dto.JourneySearchRequest;

@Tag(name = "Journeys")
@RestController
@RequestMapping("/api/v1/journeys")
class JourneyController {

    private final JourneyService journeyService;

    JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @Operation(summary = "Plan journeys between two stations",
            description = "Accessibility preferences are passed to TfL's journey planner unchanged. Results reflect TfL's "
                    + "data and may not account for every barrier. Returns 404 /problems/no-journey-found when TfL finds none.")
    @GetMapping
    LiveData<JourneyPlanDto> plan(@ParameterObject @Valid @ModelAttribute JourneySearchRequest request) {
        return journeyService.plan(request);
    }
}
