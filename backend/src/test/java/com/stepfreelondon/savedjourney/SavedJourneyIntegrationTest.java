package com.stepfreelondon.savedjourney;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stepfreelondon.ApiClient;
import com.stepfreelondon.ApiClient.Response;
import com.stepfreelondon.TestcontainersConfiguration;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.TflNotFoundException;
import com.stepfreelondon.tfl.model.TflStopPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** Saved journeys over real HTTP with real sessions. TfL is mocked with captured fixtures. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SavedJourneyIntegrationTest {

    private static final String PASSWORD = "correct horse battery";
    private static final String PATH = "/api/v1/saved-journeys";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private TflClient tflClient;

    @BeforeEach
    void stubTfl() {
        given(tflClient.stopPoint("940GZZLUWYP")).willReturn(TflFixtures.read("stop-point-wembley-park.json", TflStopPoint.class));
        given(tflClient.stopPoint("HUBWMB")).willReturn(TflFixtures.read("stop-point-hub-wembley-central.json", TflStopPoint.class));
        given(tflClient.stopPoint("940GZZLUNOPE")).willThrow(new TflNotFoundException("stop-point", "not found"));
    }

    private ApiClient signedInUser() {
        ApiClient client = new ApiClient(port);
        Response response = client.post("/api/v1/auth/register",
                Map.of("fullName", "Test User", "email", "user-" + UUID.randomUUID() + "@example.com", "password", PASSWORD));
        assertThat(response.status()).isEqualTo(201);
        return client;
    }

    private static Map<String, Object> journey(String name) {
        return Map.of(
                "name", name,
                "originId", "940GZZLUWYP",
                "destinationId", "HUBWMB",
                "accessibility", List.of("STEP_FREE_TO_PLATFORM", "NO_ESCALATORS"),
                "preference", "LEAST_INTERCHANGE",
                "route", Map.of(
                        "lineIds", List.of("metropolitan", "bakerloo"),
                        "stations", List.of(
                                Map.of("id", "940GZZLUWYP", "role", "ORIGIN"),
                                Map.of("id", "940GZZLUFYR", "role", "INTERMEDIATE"),
                                Map.of("id", "940GZZLUBST", "role", "INTERCHANGE"),
                                Map.of("id", "940GZZLUBST", "role", "INTERCHANGE"),
                                Map.of("id", "940GZZLUWYC", "role", "DESTINATION"))));
    }

    @Test
    void createReadUpdateDelete() {
        ApiClient user = signedInUser();

        Response created = user.post(PATH, journey("Home to stadium"));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().get("id").asString();
        assertThat(created.header("Location")).isEqualTo(PATH + "/" + id);
        assertThat(created.body().get("origin").get("name").asString()).isEqualTo("Wembley Park");
        assertThat(created.body().get("origin").get("icsCode").asString()).isEqualTo("1000257");
        assertThat(created.body().get("destination").get("icsCode").asString()).isEqualTo("1000256");
        assertThat(created.body().get("monitoringEnabled").asBoolean()).isTrue();
        assertThat(created.body().get("lineIds").valueStream().map(n -> n.asString())).containsExactly("metropolitan", "bakerloo");
        // The duplicate interchange stop was collapsed; order is preserved.
        assertThat(created.body().get("stations").findValuesAsString("id"))
                .containsExactly("940GZZLUWYP", "940GZZLUFYR", "940GZZLUBST", "940GZZLUWYC");

        assertThat(user.get(PATH).body().valueStream().map(j -> j.get("name").asString())).containsExactly("Home to stadium");
        assertThat(user.get(PATH + "/" + id).body().get("preference").asString()).isEqualTo("LEAST_INTERCHANGE");

        Response updated = user.patch(PATH + "/" + id, Map.of("name", "Match day", "monitoringEnabled", false));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("name").asString()).isEqualTo("Match day");
        assertThat(updated.body().get("monitoringEnabled").asBoolean()).isFalse();
        // The route was not rewritten by the update.
        assertThat(updated.body().get("stations").size()).isEqualTo(4);

        assertThat(user.delete(PATH + "/" + id, null).status()).isEqualTo(204);
        assertThat(user.get(PATH + "/" + id).status()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from saved_journey_station where saved_journey_id = ?::uuid",
                Integer.class, id)).isZero();
    }

    @Test
    void oneUserCannotSeeOrChangeAnotherUsersJourneys() {
        ApiClient alice = signedInUser();
        ApiClient bob = signedInUser();
        String alicesJourney = alice.post(PATH, journey("Alice's commute")).body().get("id").asString();

        assertThat(bob.get(PATH).body().size()).isZero();
        Response read = bob.get(PATH + "/" + alicesJourney);
        Response update = bob.patch(PATH + "/" + alicesJourney, Map.of("name", "Hijacked"));
        Response delete = bob.delete(PATH + "/" + alicesJourney, null);
        Response missing = bob.get(PATH + "/" + UUID.randomUUID());

        // 404, identical to a journey that doesn't exist: Bob can't even learn that the id is real.
        assertThat(List.of(read.status(), update.status(), delete.status())).containsOnly(404);
        assertThat(read.body().get("detail")).isEqualTo(missing.body().get("detail"));
        Response stillThere = alice.get(PATH + "/" + alicesJourney);
        assertThat(stillThere.status()).isEqualTo(200);
        assertThat(stillThere.body().get("name").asString()).isEqualTo("Alice's commute");
    }

    @Test
    void namesAreUniquePerUserNotGlobally() {
        ApiClient alice = signedInUser();
        ApiClient bob = signedInUser();

        assertThat(alice.post(PATH, journey("Commute")).status()).isEqualTo(201);
        Response duplicate = alice.post(PATH, journey(" Commute "));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("type").asString()).isEqualTo("/problems/duplicate-journey-name");
        assertThat(bob.post(PATH, journey("Commute")).status()).isEqualTo(201);

        String second = alice.post(PATH, journey("Weekend")).body().get("id").asString();
        assertThat(alice.patch(PATH + "/" + second, Map.of("name", "Commute")).status()).isEqualTo(409);
    }

    @Test
    void stationsAreStoredOnceAndShared() {
        signedInUser().post(PATH, journey("A"));
        signedInUser().post(PATH, journey("B"));

        assertThat(jdbc.queryForObject("select count(*) from station where tfl_station_id = '940GZZLUWYP'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void unknownStationIsAValidationError() {
        Map<String, Object> body = new java.util.HashMap<>(journey("Nowhere"));
        body.put("destinationId", "940GZZLUNOPE");

        Response response = signedInUser().post(PATH, body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("errors").get(0).get("field").asString()).isEqualTo("destinationId");
    }

    @Test
    void malformedRequestsAreValidationErrors() {
        Map<String, Object> body = new java.util.HashMap<>(journey(""));
        body.put("originId", "../etc/passwd");
        body.put("route", Map.of("lineIds", List.of("Not A Line!"), "stations", List.of()));

        Response response = signedInUser().post(PATH, body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("errors").findValuesAsString("field"))
                .contains("name", "originId", "route.stations", "route.lineIds[0]");
        assertThat(signedInUser().get(PATH + "/not-a-uuid").status()).isEqualTo(400);
    }

    @Test
    void savedJourneysRequireASignedInUser() {
        ApiClient anonymous = new ApiClient(port);

        assertThat(anonymous.get(PATH).status()).isEqualTo(401);
        assertThat(anonymous.post(PATH, journey("Sneaky")).status()).isEqualTo(401);
    }

    @Test
    void deletingTheAccountDeletesItsJourneysAndRoutes() {
        ApiClient user = signedInUser();
        String id = user.post(PATH, journey("Doomed")).body().get("id").asString();

        assertThat(user.delete("/api/v1/auth/me", Map.of("password", PASSWORD)).status()).isEqualTo(204);

        assertThat(jdbc.queryForObject("select count(*) from saved_journey where id = ?::uuid", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from saved_journey_line where saved_journey_id = ?::uuid",
                Integer.class, id)).isZero();
    }
}
