package com.stepfreelondon.tfl;

/** TfL returned a server error, timed out, was unreachable, or sent a response we could not read. */
public final class TflUnavailableException extends TflTransientException {

    public TflUnavailableException(String endpoint, String message) {
        super(endpoint, message);
    }
}
