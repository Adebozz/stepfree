package com.stepfreelondon.accessibility;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.accessibility.dto.StationAccessibilityDto;
import com.stepfreelondon.accessibility.dto.StationDetailDto;
import com.stepfreelondon.common.live.LiveData;

@Tag(name = "Stations")
@RestController
@RequestMapping("/api/v1/stations/{id}")
class StationProfileController {

    private static final String STATION_ID = "^[A-Za-z0-9]{3,20}$";

    private final StationProfileService stationProfileService;

    StationProfileController(StationProfileService stationProfileService) {
        this.stationProfileService = stationProfileService;
    }

    @Operation(summary = "Station details")
    @GetMapping
    LiveData<StationDetailDto> detail(
            @Parameter(description = "TfL NaPTAN or hub id", example = "940GZZLUWYP")
            @PathVariable @Pattern(regexp = STATION_ID, message = "must be a TfL station id") String id) {
        return stationProfileService.detail(id);
    }

    @Operation(summary = "Accessibility information, facilities, lift outages and disruptions for a station",
            description = "Fields TfL does not publish are returned with a null value, meaning information unavailable.")
    @GetMapping("/accessibility")
    LiveData<StationAccessibilityDto> accessibility(
            @Parameter(description = "TfL NaPTAN or hub id", example = "940GZZLUWYP")
            @PathVariable @Pattern(regexp = STATION_ID, message = "must be a TfL station id") String id) {
        return stationProfileService.accessibility(id);
    }
}
