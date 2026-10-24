package com.stepfreelondon.config;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.stepfreelondon.ApiClient;
import com.stepfreelondon.ApiClient.Response;
import com.stepfreelondon.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** The SPA is served from the API's origin; client routes fall back to index.html, server paths never do. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpaAndSecurityHeadersTest {

    @LocalServerPort
    private int port;

    @Test
    void clientRoutesGetTheAppShell() {
        ApiClient client = new ApiClient(port);
        for (String path : new String[] {"/", "/saved", "/journey/0?from=1000257", "/stations/940GZZLUWYP"}) {
            Response response = client.getRaw(path);
            assertThat(response.status()).as(path).isEqualTo(200);
            assertThat(response.text()).as(path).contains("StepFree London test shell");
        }
    }

    @Test
    void serverPathsAndMissingFilesAreNotReplacedByTheShell() {
        ApiClient client = new ApiClient(port);
        assertThat(client.getRaw("/assets/missing.js").status()).isEqualTo(404);
        assertThat(client.getRaw("/actuator/env").status()).isEqualTo(404);
        Response api = client.getRaw("/api/v1/does-not-exist");
        assertThat(api.status()).isEqualTo(401); // protected prefix: never HTML
        assertThat(api.header("Content-Type")).contains("application/problem+json");
    }

    @Test
    void monitoringTriggerIsOffWithoutAToken() {
        // No MONITORING_TRIGGER_TOKEN in this context: the endpoint pretends not to exist, even without CSRF.
        assertThat(new ApiClient(port).postWithoutCsrf("/internal/monitoring/run", null).status()).isEqualTo(404);
    }

    @Test
    void securityHeadersAreSent() {
        Response response = new ApiClient(port).getRaw("/");
        assertThat(response.header("Content-Security-Policy"))
                .contains("script-src 'self'", "img-src 'self' data: https://tile.openstreetmap.org", "frame-ancestors 'none'");
        assertThat(response.header("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void onlyExtensionlessPathsOutsideServerPrefixesAreClientRoutes() {
        assertThat(SpaWebConfig.isClientRoute("saved")).isTrue();
        assertThat(SpaWebConfig.isClientRoute("journey/0")).isTrue();
        assertThat(SpaWebConfig.isClientRoute("assets/index.js")).isFalse();
        assertThat(SpaWebConfig.isClientRoute("api/v1/x")).isFalse();
        assertThat(SpaWebConfig.isClientRoute("actuator/health")).isFalse();
        assertThat(SpaWebConfig.isClientRoute("internal/monitoring/run")).isFalse();
    }
}
