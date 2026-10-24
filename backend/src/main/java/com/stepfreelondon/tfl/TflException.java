package com.stepfreelondon.tfl;

/**
 * Base type for every failure talking to TfL. Subclasses tell callers what kind of failure it was,
 * so they can decide between falling back to cached data, returning 404, or reporting an outage.
 *
 * <p>Messages never contain request URLs, because those can carry the {@code app_key}.
 */
public abstract sealed class TflException extends RuntimeException
        permits TflTransientException, TflNotFoundException, TflBadRequestException {

    private final String endpoint;

    protected TflException(String endpoint, String message) {
        super(message);
        this.endpoint = endpoint;
    }

    /** Logical endpoint name (e.g. {@code stop-point-search}), safe to log. */
    public String endpoint() {
        return endpoint;
    }
}
