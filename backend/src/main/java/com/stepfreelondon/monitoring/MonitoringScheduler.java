package com.stepfreelondon.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stepfreelondon.notification.EmailDispatcher;

/**
 * Runs a cycle, then sends any emails it queued. {@code fixedDelay} counts from the end of the previous
 * run, so a slow TfL response delays the next cycle instead of overlapping it.
 *
 * <p>Only runs while the process is awake: a host that sleeps when idle stops monitoring
 * (risk R1, addressed in Phase 6).
 */
@Component
@ConditionalOnBooleanProperty("stepfree.monitoring.enabled")
class MonitoringScheduler {

    private final MonitoringService monitoringService;
    private final EmailDispatcher emailDispatcher;

    MonitoringScheduler(MonitoringService monitoringService, EmailDispatcher emailDispatcher) {
        this.monitoringService = monitoringService;
        this.emailDispatcher = emailDispatcher;
    }

    @Scheduled(fixedDelayString = "${stepfree.monitoring.interval}", initialDelayString = "${stepfree.monitoring.initial-delay}")
    void run() {
        monitoringService.runCycle();
        emailDispatcher.dispatchDue();
    }
}
