package com.stepfreelondon.savedjourney;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

class DuplicateJourneyNameException extends ProblemException {

    static final URI TYPE = URI.create("/problems/duplicate-journey-name");

    DuplicateJourneyNameException() {
        super(HttpStatus.CONFLICT, TYPE, "Name already used", "You already have a saved journey with this name.");
    }
}
