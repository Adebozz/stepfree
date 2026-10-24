package com.stepfreelondon.auth;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;
import com.stepfreelondon.common.error.ProblemTypes;

/** The session refers to an account that no longer exists (deleted in another session). */
class AccountNotFoundException extends ProblemException {

    AccountNotFoundException() {
        super(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHENTICATED, "Authentication required", "You need to sign in to access this resource.");
    }
}
