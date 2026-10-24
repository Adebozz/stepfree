package com.stepfreelondon.tfl;

import java.time.Duration;
import java.util.Optional;

/** TfL rejected the request because our quota was exceeded (HTTP 429, or the legacy 420). */
public final class TflRateLimitedException extends TflTransientException {

    private final Duration retryAfter;

    public TflRateLimitedException(String endpoint, Duration retryAfter) {
        super(endpoint, "TfL rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
