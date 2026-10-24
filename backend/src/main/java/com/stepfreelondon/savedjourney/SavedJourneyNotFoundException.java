package com.stepfreelondon.savedjourney;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;
import com.stepfreelondon.common.error.ProblemTypes;

/**
 * Used both when the journey doesn't exist and when it belongs to someone else. A 403 for the
 * second case would confirm that the id exists.
 */
class SavedJourneyNotFoundException extends ProblemException {

    SavedJourneyNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found", "Saved journey not found.");
    }
}
