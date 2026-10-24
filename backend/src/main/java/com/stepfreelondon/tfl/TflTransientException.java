package com.stepfreelondon.tfl;

/**
 * TfL could not serve the request right now (outage, timeout, rate limit). Callers may fall back to
 * clearly-labelled cached data.
 */
public abstract sealed class TflTransientException extends TflException
        permits TflUnavailableException, TflRateLimitedException {

    protected TflTransientException(String endpoint, String message) {
        super(endpoint, message);
    }
}
