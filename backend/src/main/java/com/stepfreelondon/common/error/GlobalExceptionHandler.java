package com.stepfreelondon.common.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.stepfreelondon.common.web.CorrelationIdFilter;
import com.stepfreelondon.tfl.TflBadRequestException;
import com.stepfreelondon.tfl.TflNotFoundException;
import com.stepfreelondon.tfl.TflRateLimitedException;
import com.stepfreelondon.tfl.TflUnavailableException;

/**
 * Turns every exception into an RFC 9457 {@code application/problem+json} body carrying the
 * request's correlation id. Spring MVC's own exceptions (bad parameters, unknown routes, wrong
 * method) are handled by the {@link ResponseEntityExceptionHandler} base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TflUnavailableException.class)
    ResponseEntity<ProblemDetail> tflUnavailable(TflUnavailableException e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.UPSTREAM_UNAVAILABLE,
                "Live TfL data unavailable",
                "Transport for London's data service could not be reached. Please try again shortly.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(TflRateLimitedException.class)
    ResponseEntity<ProblemDetail> tflRateLimited(TflRateLimitedException e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.UPSTREAM_RATE_LIMITED,
                "Live TfL data temporarily limited",
                "We're receiving a lot of requests. Please try again in a minute.");
        long retryAfterSeconds = e.retryAfter().map(d -> Math.max(1, d.toSeconds())).orElse(60L);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(TflNotFoundException.class)
    ResponseEntity<ProblemDetail> tflNotFound(TflNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND,
                "Not found", "TfL does not recognise the requested station or line."));
    }

    @ExceptionHandler(TflBadRequestException.class)
    ResponseEntity<ProblemDetail> tflBadRequest(TflBadRequestException e) {
        // We validate before calling TfL, so this indicates our bug or an API change, not user error.
        log.error("TfL rejected a request we considered valid on {}: {}", e.endpoint(), e.getMessage());
        return internalError();
    }

    @ExceptionHandler(ProblemException.class)
    ResponseEntity<ProblemDetail> domainProblem(ProblemException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.status());
        e.retryAfter().ifPresent(d -> response.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, d.toSeconds()))));
        return response.body(problem(e.status(), e.type(), e.title(), e.getMessage()));
    }

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<Object> requestValidation(RequestValidationException e, WebRequest request) {
        return validationProblem(List.of(Map.of("field", e.field(), "message", e.getMessage())), new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return internalError();
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", String.valueOf(result.getMethodParameter().getParameterName()),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList();
        return validationProblem(errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Field errors cover per-parameter problems; global errors cover cross-field rules. A binding
        // failure (e.g. an unknown enum value) gets a plain message instead of Spring's conversion text.
        List<Map<String, String>> errors = new java.util.ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(Map.of(
                "field", error.getField(),
                "message", error.isBindingFailure() ? "has an invalid value" : String.valueOf(error.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errors.add(Map.of(
                "field", error.getObjectName(), "message", String.valueOf(error.getDefaultMessage()))));
        return validationProblem(errors, headers, request);
    }

    /** Adds the correlation id to problems produced by the base class. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            addCorrelationId(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationProblem(List<Map<String, String>> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Invalid request", "One or more request values are invalid.");
        problem.setProperty("errors", errors);
        return createResponseEntity(problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ResponseEntity<ProblemDetail> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL, "Unexpected error",
                "Something went wrong on our side. Quote the correlation id if you report this."));
    }

    static ProblemDetail problem(HttpStatus status, java.net.URI type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        addCorrelationId(problem);
        return problem;
    }

    private static void addCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
