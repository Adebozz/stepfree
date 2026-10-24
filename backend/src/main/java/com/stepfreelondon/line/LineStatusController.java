package com.stepfreelondon.line;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.common.live.LiveData;
import com.stepfreelondon.line.dto.LineStatusDto;

@Tag(name = "Lines")
@RestController
@RequestMapping("/api/v1/lines")
class LineStatusController {

    private final LineStatusService lineStatusService;

    LineStatusController(LineStatusService lineStatusService) {
        this.lineStatusService = lineStatusService;
    }

    @Operation(summary = "Current status of Tube, Elizabeth line, Overground, DLR and Tram lines",
            description = "Cached for up to 60 seconds. If TfL is unreachable, the last good copy is returned with stale=true.")
    @GetMapping("/status")
    LiveData<List<LineStatusDto>> status() {
        return lineStatusService.currentStatus();
    }
}
