package com.stepfreelondon.auth;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

/**
 * Registration with an email that has an account. This does reveal that the email is registered;
 * without email verification there is no way to avoid that and still tell a real user what went
 * wrong. Login, the endpoint attackers would probe at scale, never reveals it.
 */
class EmailAlreadyRegisteredException extends ProblemException {

    static final URI TYPE = URI.create("/problems/email-already-registered");

    EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, TYPE, "Email already registered", "An account with this email already exists. Try signing in.");
    }
}
