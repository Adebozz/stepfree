package com.stepfreelondon.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code stepfree.auth}. */
@ConfigurationProperties("stepfree.auth")
record AuthProperties(int maxFailuresPerEmail, int maxFailuresPerIp, Duration failureWindow) {
}
