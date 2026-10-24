package com.stepfreelondon.monitoring;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stepfreelondon.ApiClient;
import com.stepfreelondon.ApiClient.Response;
import com.stepfreelondon.TestcontainersConfiguration;
import com.stepfreelondon.notification.EmailDispatcher;
import com.stepfreelondon.notification.EmailSender;
import com.stepfreelondon.tfl.TflClient;
import com.stepfreelondon.tfl.TflFixtures;
import com.stepfreelondon.tfl.model.TflStopPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Monitoring cycles against a real database: change detection, matching, alert dedup, the email
 * outbox and the alerts API. The TfL feed is replaced with hand-built snapshots so each test controls
 * exactly what TfL "reports"; normalising real TfL data is covered by {@link DisruptionNormaliserTest}.
 *
 * <p>Each test uses its own random line id, so journeys saved by other test classes never match.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoringIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MonitoringService monitoring;

    @Autowired
    private EmailDispatcher emailDispatcher;

    @MockitoBean
    private DisruptionFeed feed;

    @MockitoBean
    private EmailSender emailSender;

    @MockitoBean
    private TflClient tflClient;

    private String line;

    @BeforeEach
    void setUp() {
        // Events are global, not per user: start each test from no active disruptions.
        jdbc.update("DELETE FROM disruption_event");
        jdbc.update("DELETE FROM monitoring_run");
        line = "test-" + UUID.randomUUID().toString().substring(0, 8);
        given(tflClient.stopPoint("940GZZLUWYP")).willReturn(TflFixtures.read("stop-point-wembley-park.json", TflStopPoint.class));
        given(tflClient.stopPoint("HUBWMB")).willReturn(TflFixtures.read("stop-point-hub-wembley-central.json", TflStopPoint.class));
    }

    @Test
    void newUpdatedAndResolvedAlertsAreCreatedOnceEach() {
        User user = register(true);
        String journeyId = saveJourney(user, "Commute");

        MonitoringResult first = runWith(lineEvent("Minor Delays", "h1"));
        assertThat(first.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(first.eventsCreated()).isEqualTo(1);
        assertThat(alertTypes(user)).containsExactly("NEW");

        // The same snapshot again: nothing new, however many times the cycle runs.
        assertThat(runWith(lineEvent("Minor Delays", "h1")).alertsCreated()).isZero();
        runWith(lineEvent("Minor Delays", "h1"));
        assertThat(alertTypes(user)).containsExactly("NEW");

        MonitoringResult worse = runWith(lineEvent("Severe Delays", "h2"));
        assertThat(worse.eventsUpdated()).isEqualTo(1);
        assertThat(alertTypes(user)).containsExactly("UPDATED", "NEW");

        MonitoringResult gone = runWith(Map.of(DisruptionSource.LINE_STATUS, List.of()));
        assertThat(gone.eventsResolved()).isEqualTo(1);
        assertThat(alertTypes(user)).containsExactly("RESOLVED", "UPDATED", "NEW");

        Response alerts = user.client().get("/api/v1/alerts");
        assertThat(alerts.body().get("unreadCount").asInt()).isEqualTo(3);
        var latest = alerts.body().get("alerts").get(0);
        assertThat(latest.get("journeyId").asString()).isEqualTo(journeyId);
        assertThat(latest.get("journeyName").asString()).isEqualTo("Commute");
        assertThat(latest.get("title").asString()).isEqualTo("No longer reported: " + line + ": Severe Delays");
        assertThat(latest.get("message").asString()).startsWith("TfL no longer reports this disruption.");

        assertThat(jdbc.queryForObject("SELECT status FROM disruption_event WHERE identity_key = ?", String.class, line))
                .isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT revision FROM disruption_event WHERE identity_key = ?", Integer.class, line))
                .isEqualTo(2);
        // One email queued per alert (the outbox), none sent yet.
        assertThat(deliveryStatuses(user)).containsExactly("PENDING", "PENDING", "PENDING");
    }

    @Test
    void aFeedThatFailsDoesNotResolveItsEvents() {
        User user = register(false);
        saveJourney(user, "Commute");
        runWith(lineEvent("Minor Delays", "h1"));

        // Line status unreadable this cycle; station notices read fine.
        given(feed.read()).willReturn(new FeedSnapshot(Map.of(DisruptionSource.STOP_POINT, List.of()), List.of("LINE_STATUS: unavailable")));
        MonitoringResult result = monitoring.runCycle();

        assertThat(result.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(result.eventsResolved()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM disruption_event WHERE identity_key = ?", String.class, line))
                .isEqualTo("ACTIVE");
        assertThat(alertTypes(user)).containsExactly("NEW");
        assertThat(jdbc.queryForObject("SELECT error_summary FROM monitoring_run ORDER BY started_at DESC LIMIT 1", String.class))
                .isEqualTo("LINE_STATUS: unavailable");
    }

    @Test
    void everyFeedFailingChangesNothing() {
        User user = register(false);
        saveJourney(user, "Commute");
        runWith(lineEvent("Minor Delays", "h1"));

        given(feed.read()).willReturn(new FeedSnapshot(Map.of(), List.of("LINE_STATUS: x", "STOP_POINT: x", "LIFT: x")));
        assertThat(monitoring.runCycle().status()).isEqualTo(RunStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM disruption_event WHERE status = 'ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void onlyAffectedAndMonitoredJourneysAreAlerted() {
        User user = register(false);
        saveJourney(user, "Affected");
        String paused = saveJourney(user, "Paused");
        user.client().patch("/api/v1/saved-journeys/" + paused, Map.of("monitoringEnabled", false));
        User optedOut = register(false);
        saveJourney(optedOut, "Opted out");
        optedOut.client().put("/api/v1/notification-preferences", Map.of("emailEnabled", false, "monitoringEnabled", false));

        var otherLine = new ObservedDisruption(DisruptionSource.LINE_STATUS, "other-" + line, "h", null, null, 9, "Minor Delays",
                "Other: Minor Delays", "Delays elsewhere", null, null, Set.of("other-" + line), Set.of());
        Map<DisruptionSource, List<ObservedDisruption>> snapshot = new EnumMap<>(lineEvent("Minor Delays", "h1"));
        snapshot.put(DisruptionSource.LINE_STATUS, List.of(snapshot.get(DisruptionSource.LINE_STATUS).getFirst(), otherLine));
        runWith(snapshot);

        Response alerts = user.client().get("/api/v1/alerts");
        assertThat(alerts.body().get("alerts").valueStream().map(a -> a.get("journeyName").asString()).toList())
                .containsExactly("Affected");
        assertThat(alertTypes(optedOut)).isEmpty();
        // Email is off by default, so nothing was queued.
        assertThat(deliveryStatuses(user)).isEmpty();
    }

    @Test
    void liftOutageAlertsWhereThePassengerChangesButNotWhereTheyRideThrough() {
        User user = register(false);
        saveJourney(user, "Commute");

        runWith(Map.of(DisruptionSource.LIFT, List.of(
                lift("940GZZLUBST", "Baker Street: lift out of service"),
                lift("940GZZLUFYR", "Finchley Road: lift out of service"))));

        Response alerts = user.client().get("/api/v1/alerts");
        assertThat(alerts.body().get("alerts").valueStream().map(a -> a.get("message").asString()).toList())
                .containsExactly("Baker Street: lift out of service");
    }

    @Test
    void concurrentCyclesStillAlertOnce() throws Exception {
        User user = register(true);
        saveJourney(user, "Commute");
        given(feed.read()).willReturn(new FeedSnapshot(lineEvent("Minor Delays", "h1"), List.of()));

        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MonitoringResult>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return monitoring.runCycle();
                }));
            }
            start.countDown();
            for (Future<MonitoringResult> result : results) {
                assertThat(result.get().status()).isIn(RunStatus.SUCCEEDED, RunStatus.SKIPPED);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(alertTypes(user)).containsExactly("NEW");
        assertThat(deliveryStatuses(user)).containsExactly("PENDING");
    }

    @Test
    void queuedEmailsAreSentRetriedAndCancelled() {
        User user = register(true);
        saveJourney(user, "Commute");
        runWith(lineEvent("Minor Delays", "h1"));

        willThrow(new MailSendException("SMTP down")).given(emailSender).send(anyString(), anyString(), anyString());
        assertThat(emailDispatcher.dispatchDue()).isZero();
        assertThat(jdbc.queryForMap("""
                SELECT d.delivery_status, d.attempt_count, d.failure_reason, d.next_attempt_at > now() AS later
                FROM notification_delivery d JOIN journey_alert a ON a.id = d.journey_alert_id WHERE a.user_id = ?::uuid""", user.id()))
                .containsEntry("delivery_status", "PENDING").containsEntry("attempt_count", 1)
                .containsEntry("failure_reason", "MailSendException").containsEntry("later", true);

        // Not due yet: an immediate second dispatch doesn't retry.
        reset(emailSender);
        emailDispatcher.dispatchDue();
        verify(emailSender, never()).send(anyString(), anyString(), anyString());

        jdbc.update("UPDATE notification_delivery SET next_attempt_at = now() - interval '1 second'");
        assertThat(emailDispatcher.dispatchDue()).isEqualTo(1);
        verify(emailSender, times(1)).send(eq(user.email()), contains(line + ": Minor Delays"), contains("Saved journey: Commute"));
        assertThat(deliveryStatuses(user)).containsExactly("SENT");

        // Turned off after the alert was queued: the email is cancelled, not sent.
        runWith(lineEvent("Severe Delays", "h2"));
        user.client().put("/api/v1/notification-preferences", Map.of("emailEnabled", false, "monitoringEnabled", true));
        reset(emailSender);
        emailDispatcher.dispatchDue();
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        assertThat(deliveryStatuses(user)).containsExactlyInAnyOrder("SENT", "CANCELLED");
    }

    @Test
    void usersSeeAndChangeOnlyTheirOwnAlerts() {
        User owner = register(false);
        saveJourney(owner, "Commute");
        runWith(lineEvent("Minor Delays", "h1"));
        String alertId = owner.client().get("/api/v1/alerts").body().get("alerts").get(0).get("id").asString();

        User other = register(false);
        assertThat(other.client().get("/api/v1/alerts").body().get("alerts").isEmpty()).isTrue();
        assertThat(other.client().post("/api/v1/alerts/" + alertId + "/read", null).status()).isEqualTo(404);
        assertThat(other.client().post("/api/v1/alerts/" + UUID.randomUUID() + "/read", null).status()).isEqualTo(404);
        other.client().post("/api/v1/alerts/read-all", null);
        assertThat(owner.client().get("/api/v1/alerts").body().get("unreadCount").asInt()).isEqualTo(1);

        assertThat(owner.client().post("/api/v1/alerts/" + alertId + "/read", null).status()).isEqualTo(204);
        Response after = owner.client().get("/api/v1/alerts");
        assertThat(after.body().get("unreadCount").asInt()).isZero();
        assertThat(after.body().get("alerts").get(0).get("read").asBoolean()).isTrue();
        assertThat(owner.client().get("/api/v1/alerts?unreadOnly=true").body().get("alerts").isEmpty()).isTrue();

        assertThat(new ApiClient(port).get("/api/v1/alerts").status()).isEqualTo(401);
    }

    @Test
    void preferencesDefaultValidateAndPersist() {
        User user = register(false);
        Response defaults = user.client().get("/api/v1/notification-preferences");
        assertThat(defaults.body().get("emailEnabled").asBoolean()).isFalse();
        assertThat(defaults.body().get("monitoringEnabled").asBoolean()).isTrue();

        assertThat(user.client().put("/api/v1/notification-preferences", Map.of("emailEnabled", true)).status()).isEqualTo(400);
        assertThat(user.client().put("/api/v1/notification-preferences", Map.of("emailEnabled", true, "monitoringEnabled", true))
                .status()).isEqualTo(200);
        assertThat(user.client().get("/api/v1/notification-preferences").body().get("emailEnabled").asBoolean()).isTrue();
    }

    @Test
    void monitoringStatusReportsTheLastCompletedRun() {
        User user = register(false);
        Response before = user.client().get("/api/v1/monitoring/status");
        assertThat(before.status()).isEqualTo(200);
        assertThat(before.body().get("schedulerEnabled").asBoolean()).isFalse();
        assertThat(before.body().get("lastCheckedAt").isNull()).isTrue();

        runWith(lineEvent("Minor Delays", "h1"));
        Response after = user.client().get("/api/v1/monitoring/status");
        assertThat(after.body().get("lastStatus").asString()).isEqualTo("SUCCEEDED");
        assertThat(after.body().get("lastCheckedAt").isNull()).isFalse();
    }

    @Test
    void deletingTheAccountDeletesAlertsDeliveriesAndPreferences() {
        User user = register(true);
        saveJourney(user, "Commute");
        runWith(lineEvent("Minor Delays", "h1"));
        assertThat(alertTypes(user)).hasSize(1);

        assertThat(user.client().delete("/api/v1/auth/me", Map.of("password", PASSWORD)).status()).isEqualTo(204);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM journey_alert WHERE user_id = ?::uuid", Integer.class, user.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preference WHERE user_id = ?::uuid", Integer.class, user.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_delivery", Integer.class)).isZero();
        // The disruption itself holds no personal data and stays.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM disruption_event WHERE identity_key = ?", Integer.class, line)).isEqualTo(1);
    }

    // --- helpers ---

    private record User(ApiClient client, String id, String email) {
    }

    private User register(boolean emailAlerts) {
        ApiClient client = new ApiClient(port);
        String email = "monitor-" + UUID.randomUUID() + "@example.com";
        Response response = client.post("/api/v1/auth/register", Map.of("fullName", "Test User", "email", email, "password", PASSWORD));
        assertThat(response.status()).isEqualTo(201);
        if (emailAlerts) {
            assertThat(client.put("/api/v1/notification-preferences", Map.of("emailEnabled", true, "monitoringEnabled", true))
                    .status()).isEqualTo(200);
        }
        return new User(client, response.body().get("id").asString(), email);
    }

    /** Wembley Park → Baker Street on this test's line, change, → Wembley Central. */
    private String saveJourney(User user, String name) {
        Response response = user.client().post("/api/v1/saved-journeys", Map.of(
                "name", name,
                "originId", "940GZZLUWYP",
                "destinationId", "HUBWMB",
                "accessibility", List.of("STEP_FREE_TO_PLATFORM"),
                "route", Map.of(
                        "lineIds", List.of(line, "bakerloo"),
                        "stations", List.of(
                                Map.of("id", "940GZZLUWYP", "role", "ORIGIN"),
                                Map.of("id", "940GZZLUFYR", "role", "INTERMEDIATE"),
                                Map.of("id", "940GZZLUBST", "role", "INTERCHANGE"),
                                Map.of("id", "940GZZLUWYC", "role", "DESTINATION")))));
        assertThat(response.status()).isEqualTo(201);
        return response.body().get("id").asString();
    }

    private Map<DisruptionSource, List<ObservedDisruption>> lineEvent(String severity, String hash) {
        return Map.of(DisruptionSource.LINE_STATUS, List.of(new ObservedDisruption(DisruptionSource.LINE_STATUS, line, hash,
                "tube", severity, 9, severity, line + ": " + severity, severity + " due to a signal failure.", null, null,
                Set.of(line), Set.of())));
    }

    private static ObservedDisruption lift(String stationId, String message) {
        return new ObservedDisruption(DisruptionSource.LIFT, stationId, DisruptionNormaliser.sha256(message), "lift", "Lift outage",
                null, null, "Lift out of service", message, null, null, Set.of(), Set.of(stationId));
    }

    private MonitoringResult runWith(Map<DisruptionSource, List<ObservedDisruption>> observed) {
        given(feed.read()).willReturn(new FeedSnapshot(observed, List.of()));
        return monitoring.runCycle();
    }

    /** Newest first. */
    private List<String> alertTypes(User user) {
        return jdbc.queryForList("SELECT alert_type FROM journey_alert WHERE user_id = ?::uuid ORDER BY created_at DESC, event_revision DESC",
                String.class, user.id());
    }

    private List<String> deliveryStatuses(User user) {
        return jdbc.queryForList("""
                SELECT d.delivery_status FROM notification_delivery d JOIN journey_alert a ON a.id = d.journey_alert_id
                WHERE a.user_id = ?::uuid ORDER BY a.created_at""", String.class, user.id());
    }
}
