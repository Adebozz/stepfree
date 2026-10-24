package com.stepfreelondon.savedjourney;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

class SavedJourneyLimitException extends ProblemException {

    static final URI TYPE = URI.create("/problems/saved-journey-limit");

    SavedJourneyLimitException(int limit) {
        super(HttpStatus.CONFLICT, TYPE, "Saved journey limit reached",
                "You can save up to " + limit + " journeys. Delete one to save another.");
    }
}
