package com.stepfreelondon.monitoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.notification.EmailDispatcher;

/**
 * Runs one cycle on demand, for hosts that sleep when idle: an external scheduler (a GitHub Actions
 * cron, see docs/deployment.md) calls this every few minutes, which both wakes the host and runs the
 * check. Answers 404 unless {@code MONITORING_TRIGGER_TOKEN} is set, and 401 for a wrong token. The
 * comparison is constant-time so the token can't be guessed byte by byte from response times.
 */
@Hidden
@RestController
class MonitoringTriggerController {

    static final String TOKEN_HEADER = "X-Monitoring-Token";

    private final MonitoringService monitoringService;
    private final EmailDispatcher emailDispatcher;
    private final MonitoringProperties properties;

    MonitoringTriggerController(MonitoringService monitoringService, EmailDispatcher emailDispatcher, MonitoringProperties properties) {
        this.monitoringService = monitoringService;
        this.emailDispatcher = emailDispatcher;
        this.properties = properties;
    }

    @PostMapping("/internal/monitoring/run")
    ResponseEntity<MonitoringResult> run(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        if (!properties.triggerEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                properties.triggerToken().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        MonitoringResult result = monitoringService.runCycle();
        emailDispatcher.dispatchDue();
        return ResponseEntity.ok(result);
    }
}
