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
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Public read-only transport endpoints and the sign-in endpoints; everything else under {@code /api}
 * requires a signed-in session (Spring Session JDBC, HttpOnly {@code SESSION} cookie).
 *
 * <p>CSRF uses Spring Security's SPA mode: an {@code XSRF-TOKEN} cookie that the frontend echoes
 * in an {@code X-XSRF-TOKEN} header. A cross-site page can make the browser send the session cookie
 * but can't read ours, so it can't produce the header.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    /**
     * Scripts only from our origin; map tiles from OpenStreetMap. Inline styles are allowed because
     * Leaflet positions tiles with style attributes; inline scripts are not.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https://tile.openstreetmap.org; connect-src 'self'; font-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler,
                                            SecurityContextRepository securityContextRepository,
                                            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/stations/**", "/api/v1/lines/**", "/api/v1/journeys/**",
                                "/api/v1/disruptions/**", "/api/v1/auth/csrf").permitAll()
                        // Logout is public so a client with an expired session can still clear its cookies.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/logout").permitAll()
                        // Token-checked in the controller; for an external scheduler waking a sleeping host (risk R1).
                        .requestMatchers(HttpMethod.POST, "/internal/monitoring/run").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Actuator exposes only health and info (see application.yml); OpenAPI docs are public.
                        .anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // The trigger sends no cookies, so there is no session to forge requests with: CSRF doesn't apply.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository).ignoringRequestMatchers("/internal/monitoring/run"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // OSM's tile policy requires a Referer; this sends only our origin, never the path.
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
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

    /** Shared with the login endpoint, which saves the context itself (see {@code SessionAuthenticator}). */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** The same repository {@code spa()} would create; a bean so login can rotate the token. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
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
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
            config.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
            config.setAllowCredentials(true);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
