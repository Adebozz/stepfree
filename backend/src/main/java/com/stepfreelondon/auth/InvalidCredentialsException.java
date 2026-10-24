package com.stepfreelondon.auth;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

/** Deliberately the same message for an unknown email and a wrong password, so accounts can't be probed. */
class InvalidCredentialsException extends ProblemException {

    static final URI TYPE = URI.create("/problems/invalid-credentials");

    InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, TYPE, "Sign-in failed", "The email or password is incorrect.");
    }
}
