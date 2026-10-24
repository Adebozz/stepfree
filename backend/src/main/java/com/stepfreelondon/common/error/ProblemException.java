package com.stepfreelondon.common.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

/**
 * A domain error that maps directly to an RFC 9457 response. Subclasses describe a situation the
 * user can understand (e.g. "no journey found"); the message is shown to clients as {@code detail},
 * so it must never contain internal details.
 */
public abstract class ProblemException extends RuntimeException {

    private final HttpStatus status;
    private final URI type;
    private final String title;

    protected ProblemException(HttpStatus status, URI type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }
}
