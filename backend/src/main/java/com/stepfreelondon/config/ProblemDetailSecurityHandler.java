package com.stepfreelondon.config;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.stepfreelondon.common.error.ProblemTypes;
import com.stepfreelondon.common.web.CorrelationIdFilter;

/**
 * Writes 401/403 responses from the security filter chain in the same RFC 9457 format the
 * controllers use. These happen before Spring MVC runs, so {@code @RestControllerAdvice} can't.
 */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemDetailSecurityHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHENTICATED, "Authentication required",
                "You need to sign in to access this resource.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        write(response, HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden",
                "You don't have permission to perform this action.");
    }

    private void write(HttpServletResponse response, HttpStatus status, URI type, String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type.toString());
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
