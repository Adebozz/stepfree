package com.stepfreelondon.auth;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

/** The current password given to confirm a sensitive change was wrong. 403: the user is signed in. */
class WrongPasswordException extends ProblemException {

    static final URI TYPE = URI.create("/problems/wrong-password");

    WrongPasswordException() {
        super(HttpStatus.FORBIDDEN, TYPE, "Password incorrect", "Your current password is incorrect.");
    }
}
