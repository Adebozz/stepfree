package com.stepfreelondon.monitoring;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code stepfree.monitoring}.
 *
 * @param enabled           whether the in-process scheduler runs (off in tests)
 * @param interval          delay between the end of one cycle and the start of the next
 * @param initialDelay      delay after startup before the first cycle
 * @param resolvedRetention resolved events (and their alerts) older than this are deleted
 * @param runRetention      monitoring_run rows older than this are deleted
 */
@ConfigurationProperties("stepfree.monitoring")
record MonitoringProperties(boolean enabled, Duration interval, Duration initialDelay,
                            Duration resolvedRetention, Duration runRetention) {
}
