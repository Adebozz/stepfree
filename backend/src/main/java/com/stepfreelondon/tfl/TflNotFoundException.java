package com.stepfreelondon.tfl;

/** TfL does not recognise the requested entity (HTTP 404). */
public final class TflNotFoundException extends TflException {

    public TflNotFoundException(String endpoint, String message) {
        super(endpoint, message);
    }
}
