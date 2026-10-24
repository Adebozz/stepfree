package com.stepfreelondon.station;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.station.dto.StationSummaryDto;

@Tag(name = "Stations")
@RestController
@RequestMapping("/api/v1/stations")
class StationController {

    private final StationService stationService;

    StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @Operation(summary = "Search stations by name",
            description = "Partial, case-insensitive match against TfL rail stations. An empty list means no matches.")
    @GetMapping
    LiveData<List<StationSummaryDto>> search(
            @Parameter(description = "Station name or part of it", example = "Wembley")
            @RequestParam
            @NotBlank
            @Size(min = 2, max = 60)
            @Pattern(regexp = "^[\\p{L}\\p{N} .,'&()\\-]+$", message = "must contain only letters, numbers, spaces and . , ' & ( ) -")
            String query) {
        return stationService.search(query);
    }
}
