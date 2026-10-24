package com.stepfreelondon.tfl;

/**
 * TfL rejected our request parameters (HTTP 400). We validate inputs before calling TfL, so this
 * points to a bug or an API change rather than user error.
 */
public final class TflBadRequestException extends TflException {

    public TflBadRequestException(String endpoint, String message) {
        super(endpoint, message);
    }
}
