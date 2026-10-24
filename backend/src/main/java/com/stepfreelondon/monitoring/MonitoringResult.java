package com.stepfreelondon.monitoring;

/** What one cycle did; stored in {@code monitoring_run}. */
public record MonitoringResult(RunStatus status, int eventsCreated, int eventsUpdated, int eventsResolved,
                               int alertsCreated, String errorSummary) {

    static MonitoringResult skipped(String reason) {
        return new MonitoringResult(RunStatus.SKIPPED, 0, 0, 0, 0, reason);
    }

    static MonitoringResult failed(String error) {
        return new MonitoringResult(RunStatus.FAILED, 0, 0, 0, 0, error);
    }
}
