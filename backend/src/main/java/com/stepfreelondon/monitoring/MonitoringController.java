package com.stepfreelondon.monitoring;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.monitoring.dto.MonitoringStatusDto;

@Tag(name = "Monitoring")
@RestController
@RequestMapping("/api/v1/monitoring")
class MonitoringController {

    private final MonitoringService service;
    private final MonitoringProperties properties;

    MonitoringController(MonitoringService service, MonitoringProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Operation(summary = "When saved journeys were last checked against TfL")
    @GetMapping("/status")
    MonitoringStatusDto status() {
        var last = service.lastRun();
        return new MonitoringStatusDto(properties.enabled(),
                last.map(MonitoringRepository.LastRun::finishedAt).orElse(null),
                last.map(MonitoringRepository.LastRun::status).orElse(null));
    }
}
