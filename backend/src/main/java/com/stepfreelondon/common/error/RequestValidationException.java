package com.stepfreelondon.common.error;

/**
 * Validation that needs context Bean Validation lacks (e.g. "time must be within 28 days of now"
 * needs the clock). Rendered exactly like annotation-based validation errors.
 */
public class RequestValidationException extends RuntimeException {

    private final String field;

    public RequestValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
