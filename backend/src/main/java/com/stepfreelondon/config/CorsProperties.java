package com.stepfreelondon.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Exact origins allowed to call the API cross-origin, bound from {@code stepfree.cors.allowed-origins}. */
@ConfigurationProperties("stepfree.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
    }
}
