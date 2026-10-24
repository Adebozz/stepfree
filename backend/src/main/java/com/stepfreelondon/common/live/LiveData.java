package com.stepfreelondon.common.live;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Envelope for any response derived from TfL live data.
 *
 * @param data        the payload
 * @param retrievedAt when this server fetched the data from TfL (TfL feeds carry no reliable
 *                    "last updated" time, so this is the most honest freshness signal available)
 * @param stale       {@code true} when TfL could not be reached and this is the last good copy
 */
public record LiveData<T>(
        T data,
        @Schema(description = "When StepFree London retrieved this data from TfL (UTC)") Instant retrievedAt,
        @Schema(description = "True if live data was unavailable and this is an older cached copy") boolean stale) {

    public static <T> LiveData<T> fresh(T data, Instant retrievedAt) {
        return new LiveData<>(data, retrievedAt, false);
    }

    public LiveData<T> asStale() {
        return new LiveData<>(data, retrievedAt, true);
    }
}
