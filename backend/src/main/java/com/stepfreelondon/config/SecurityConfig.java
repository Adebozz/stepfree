package com.stepfreelondon.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Public read-only transport endpoints, everything else under {@code /api}
 * requires authentication (no login mechanism exists until Phase 3, so those requests get 401).
 *
 * <p>CSRF uses Spring Security's SPA mode: an {@code XSRF-TOKEN} cookie that the frontend echoes
 * in an {@code X-XSRF-TOKEN} header. This is needed once session cookies arrive in Phase 3.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/stations/**", "/api/v1/lines/**", "/api/v1/journeys/**",
                                "/api/v1/disruptions/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Actuator exposes only health and info (see application.yml); OpenAPI docs are public.
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.spa())
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }

    /**
     * Cross-origin access is off unless origins are configured. Locally the Vite dev server
     * proxies {@code /api}, and in production the API is served same-origin, so CORS is only a
     * fallback for setups that can't proxy.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.allowedOrigins().isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(properties.allowedOrigins());
            config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
            config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
            config.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
            config.setAllowCredentials(true);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
