package com.stepfreelondon.config;

import java.time.Clock;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    /** Injected instead of calling {@code Instant.now()} so time-dependent logic is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("StepFree London API")
                .version("v1")
                .description("Accessible journey planning and disruption monitoring for London, built on the TfL Unified API. "
                        + "Errors use RFC 9457 problem details. Powered by TfL Open Data.")
                .license(new License().name("TfL data: Transport Data Service terms")
                        .url("https://tfl.gov.uk/corporate/terms-and-conditions/transport-data-service")));
    }
}
