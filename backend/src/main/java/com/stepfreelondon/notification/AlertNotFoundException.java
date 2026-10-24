package com.stepfreelondon.notification;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;
import com.stepfreelondon.common.error.ProblemTypes;

/** Also used for another user's alert, so ids can't be probed. */
class AlertNotFoundException extends ProblemException {

    AlertNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found", "Alert not found.");
    }
}
