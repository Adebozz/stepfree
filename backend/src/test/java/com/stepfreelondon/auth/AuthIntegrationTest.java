package com.stepfreelondon.auth;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.stepfreelondon.ApiClient;
import com.stepfreelondon.ApiClient.Response;
import com.stepfreelondon.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full stack over real HTTP: Spring Security, Spring Session JDBC and PostgreSQL. Each test uses
 * fresh emails, because the database and the in-memory rate limiter are shared across tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ApiClient registered(String email) {
        ApiClient client = new ApiClient(port);
        Response response = client.post("/api/v1/auth/register", Map.of("fullName", "Ada Lovelace", "email", email, "password", PASSWORD));
        assertThat(response.status()).isEqualTo(201);
        return client;
    }

    @Test
    void registerSignsInWithAHardenedSessionCookie() {
        ApiClient client = new ApiClient(port);
        String email = uniqueEmail();

        Response registered = client.post("/api/v1/auth/register",
                Map.of("fullName", "  Ada Lovelace ", "email", email.toUpperCase(), "password", PASSWORD));

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.body().get("email").asString()).isEqualTo(email);
        assertThat(registered.body().get("fullName").asString()).isEqualTo("Ada Lovelace");
        assertThat(registered.body().has("passwordHash")).isFalse();
        assertThat(registered.setCookies()).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("SESSION=").contains("HttpOnly").contains("SameSite=Lax"));

        Response me = client.get("/api/v1/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().get("email").asString()).isEqualTo(email);
    }

    @Test
    void passwordsAreHashedAndNeverStoredInTheSession() {
        String email = uniqueEmail();
        registered(email);

        String hash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email);
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        // The session row is indexed by user id (not email), and its serialised attributes hold no hash.
        String userId = jdbc.queryForObject("select id::text from app_user where email = ?", String.class, email);
        assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?", Integer.class, userId))
                .isEqualTo(1);
        byte[] attributes = jdbc.queryForObject("""
                select string_agg(a.attribute_bytes, ''::bytea) from spring_session_attributes a
                join spring_session s on s.primary_id = a.session_primary_id where s.principal_name = ?""", byte[].class, userId);
        assertThat(new String(attributes, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(hash);
    }

    @Test
    void stateChangingRequestsWithoutTheCsrfHeaderAreRejected() {
        ApiClient client = new ApiClient(port);
        client.get("/api/v1/auth/csrf");

        Response response = client.postWithoutCsrf("/api/v1/auth/register",
                Map.of("fullName", "Mallory", "email", uniqueEmail(), "password", PASSWORD));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body().get("type").asString()).isEqualTo("/problems/forbidden");
    }

    @Test
    void loginIsCaseInsensitive() {
        String email = uniqueEmail();
        registered(email).post("/api/v1/auth/logout", null);
        ApiClient client = new ApiClient(port);

        assertThat(client.post("/api/v1/auth/login", Map.of("email", email.toUpperCase(), "password", PASSWORD)).status())
                .isEqualTo(200);
        assertThat(client.get("/api/v1/auth/me").status()).isEqualTo(200);
    }

    @Test
    void loginReplacesAnExistingSessionId() {
        String email = uniqueEmail();
        ApiClient client = registered(email);
        String before = sessionId(client);

        assertThat(client.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)).status()).isEqualTo(200);

        String after = sessionId(client);
        assertThat(after).isNotEqualTo(before);
        // The old id is gone from the store, so a planted or stolen pre-login id is worthless.
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class, before)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class, after)).isEqualTo(1);
    }

    /** Spring Session's cookie holds the session id base64-encoded. */
    private static String sessionId(ApiClient client) {
        return new String(java.util.Base64.getDecoder().decode(client.cookie("SESSION").orElseThrow()));
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        String email = uniqueEmail();
        registered(email);
        ApiClient client = new ApiClient(port);

        Response wrongPassword = client.post("/api/v1/auth/login", Map.of("email", email, "password", "wrong password!"));
        Response unknownEmail = client.post("/api/v1/auth/login", Map.of("email", uniqueEmail(), "password", "wrong password!"));

        assertThat(wrongPassword.status()).isEqualTo(401).isEqualTo(unknownEmail.status());
        assertThat(wrongPassword.body().get("detail")).isEqualTo(unknownEmail.body().get("detail"));
        assertThat(wrongPassword.body().get("type").asString()).isEqualTo("/problems/invalid-credentials");
    }

    @Test
    void repeatedFailuresAreRateLimitedEvenWithTheRightPassword() {
        String email = uniqueEmail();
        registered(email);
        ApiClient attacker = new ApiClient(port);
        for (int i = 0; i < 5; i++) {
            assertThat(attacker.post("/api/v1/auth/login", Map.of("email", email, "password", "guess number " + i)).status())
                    .isEqualTo(401);
        }

        Response blocked = attacker.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));

        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.body().get("type").asString()).isEqualTo("/problems/too-many-login-attempts");
        assertThat(Long.parseLong(blocked.header("Retry-After"))).isBetween(1L, 900L);
    }

    @Test
    void duplicateEmailIsAConflictRegardlessOfCase() {
        String email = uniqueEmail();
        registered(email);

        Response duplicate = new ApiClient(port).post("/api/v1/auth/register",
                Map.of("fullName", "Someone Else", "email", email.toUpperCase(), "password", PASSWORD));

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("type").asString()).isEqualTo("/problems/email-already-registered");
    }

    @Test
    void invalidRegistrationIsAValidationProblem() {
        Response response = new ApiClient(port).post("/api/v1/auth/register",
                Map.of("fullName", "", "email", "not-an-email", "password", "short"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("errors").findValuesAsString("field")).contains("fullName", "email", "password");
    }

    @Test
    void passwordsOverBcryptsByteLimitAreRejected() {
        // 30 characters but 90 bytes in UTF-8: passes @Size, fails the byte check.
        Response response = new ApiClient(port).post("/api/v1/auth/register",
                Map.of("fullName", "Ada", "email", uniqueEmail(), "password", "€".repeat(30)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("errors").get(0).get("field").asString()).isEqualTo("password");
    }

    @Test
    void logoutEndsTheSession() {
        ApiClient client = registered(uniqueEmail());

        assertThat(client.post("/api/v1/auth/logout", null).status()).isEqualTo(204);

        Response me = client.get("/api/v1/auth/me");
        assertThat(me.status()).isEqualTo(401);
        assertThat(me.body().get("type").asString()).isEqualTo("/problems/unauthenticated");
    }

    @Test
    void profileUpdateRequiresThePasswordOnlyToChangeTheEmail() {
        String email = uniqueEmail();
        ApiClient client = registered(email);

        Response renamed = client.patch("/api/v1/auth/me", Map.of("fullName", "Ada King"));
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(renamed.body().get("fullName").asString()).isEqualTo("Ada King");

        String newEmail = uniqueEmail();
        assertThat(client.patch("/api/v1/auth/me", Map.of("email", newEmail)).status()).isEqualTo(403);
        assertThat(client.patch("/api/v1/auth/me", Map.of("email", newEmail, "currentPassword", "nope nope nope")).status())
                .isEqualTo(403);
        Response changed = client.patch("/api/v1/auth/me", Map.of("email", newEmail, "currentPassword", PASSWORD));
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.body().get("email").asString()).isEqualTo(newEmail);
        // The session survives an email change because it is keyed by user id.
        assertThat(client.get("/api/v1/auth/me").body().get("email").asString()).isEqualTo(newEmail);
    }

    @Test
    void changingThePasswordSignsOutOtherSessionsButNotThisOne() {
        String email = uniqueEmail();
        ApiClient laptop = registered(email);
        ApiClient phone = new ApiClient(port);
        assertThat(phone.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)).status()).isEqualTo(200);

        Response changed = laptop.post("/api/v1/auth/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "an even better passphrase"));

        assertThat(changed.status()).isEqualTo(204);
        assertThat(laptop.get("/api/v1/auth/me").status()).isEqualTo(200);
        assertThat(phone.get("/api/v1/auth/me").status()).isEqualTo(401);
        assertThat(new ApiClient(port).post("/api/v1/auth/login",
                Map.of("email", email, "password", "an even better passphrase")).status()).isEqualTo(200);
    }

    @Test
    void deletingTheAccountRemovesTheUserAndEverySession() {
        String email = uniqueEmail();
        ApiClient laptop = registered(email);
        ApiClient phone = new ApiClient(port);
        phone.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));

        assertThat(laptop.delete("/api/v1/auth/me", Map.of("password", "wrong password!")).status()).isEqualTo(403);
        assertThat(laptop.delete("/api/v1/auth/me", Map.of("password", PASSWORD)).status()).isEqualTo(204);

        assertThat(laptop.get("/api/v1/auth/me").status()).isEqualTo(401);
        assertThat(phone.get("/api/v1/auth/me").status()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email)).isZero();
        assertThat(new ApiClient(port).post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)).status())
                .isEqualTo(401);
    }
}
