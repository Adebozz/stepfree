package com.stepfreelondon;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots with the production profile, so a mistake in application-prod.yml fails the build rather
 * than the deploy. (It once did: Hikari rejected a "60s" idle timeout at startup.) The database
 * connection comes from Testcontainers; the DB_* placeholders only need to resolve.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(properties = {"DB_URL=jdbc:postgresql://unused/unused", "DB_USERNAME=unused", "DB_PASSWORD=unused"})
class ProdProfileTest {

    @Autowired
    private Environment environment;

    @Test
    void productionConfigurationStarts() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("0");
    }
}
