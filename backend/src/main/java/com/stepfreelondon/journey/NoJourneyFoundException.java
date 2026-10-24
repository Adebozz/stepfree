package com.stepfreelondon.journey;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.stepfreelondon.common.error.ProblemException;

/** TfL found no journey for the stations and options requested. */
class NoJourneyFoundException extends ProblemException {

    static final URI TYPE = URI.create("/problems/no-journey-found");

    NoJourneyFoundException(boolean accessibilityRequested) {
        super(HttpStatus.NOT_FOUND, TYPE, "No journey found", accessibilityRequested
                ? "TfL couldn't find a journey between these stations that meets the selected accessibility options. "
                        + "Try a nearby station, a different time, or fewer options."
                : "TfL couldn't find a journey between these stations at the selected time.");
    }
}
