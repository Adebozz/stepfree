package com.stepfreelondon.auth;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

class TooManyLoginAttemptsException extends ProblemException {

    static final URI TYPE = URI.create("/problems/too-many-login-attempts");

    private final Duration retryAfter;

    TooManyLoginAttemptsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, TYPE, "Too many sign-in attempts",
                "Too many failed sign-in attempts. Please wait before trying again.");
        this.retryAfter = retryAfter;
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.of(retryAfter);
    }
}
