package com.stepfreelondon.tfl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Error body TfL returns with 4xx/5xx responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TflApiError(Integer httpStatusCode, String exceptionType, String message) {
}
