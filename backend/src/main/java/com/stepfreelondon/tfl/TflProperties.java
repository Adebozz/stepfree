package com.stepfreelondon.tfl;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the TfL Unified API client, bound from {@code stepfree.tfl.*}.
 *
 * @param baseUrl        root of the Unified API
 * @param appKey         optional subscription key; anonymous access is limited to 50 requests/minute
 * @param connectTimeout TCP connect timeout
 * @param readTimeout    time to wait for a response once connected
 * @param maxAttempts    total attempts for transient failures (1 = no retry)
 * @param initialBackoff delay before the first retry; doubles on each further retry
 */
@Validated
@ConfigurationProperties("stepfree.tfl")
public record TflProperties(
        @NotNull String baseUrl,
        String appKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) int maxAttempts,
        @NotNull Duration initialBackoff) {

    public boolean hasAppKey() {
        return appKey != null && !appKey.isBlank();
    }
}
