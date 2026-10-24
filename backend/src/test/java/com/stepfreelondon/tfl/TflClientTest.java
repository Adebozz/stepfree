package com.stepfreelondon.tfl;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.stepfreelondon.tfl.model.TflLine;
import com.stepfreelondon.tfl.model.TflStopPointSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TflClientTest {

    private static final String BASE = "https://api.tfl.test";
    private static final List<String> MODES = List.of("tube", "dlr");

    private final List<Duration> sleeps = new ArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private MockRestServiceServer server;

    private TflClient client(String appKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TflProperties properties = new TflProperties(BASE, appKey, Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofMillis(100));
        return new TflClient(builder, properties, meters, sleeps::add);
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("tfl/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void searchStopPointsParsesRealTflResponse() throws IOException {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString(BASE + "/StopPoint/Search/wembley")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(queryParam("modes", "tube,dlr"))
                .andExpect(queryParam("includeHubs", "true"))
                .andRespond(withSuccess(fixture("stop-point-search-wembley.json"), MediaType.APPLICATION_JSON));

        TflStopPointSearchResponse response = client.searchStopPoints("wembley", MODES, 15);

        server.verify();
        assertThat(response.matches()).hasSize(4);
        assertThat(response.matches().getFirst().id()).isEqualTo("HUBWMB");
        assertThat(response.matches().getFirst().icsId()).isEqualTo("1000256");
    }

    @Test
    void userInputIsEncodedAsASinglePathSegment() {
        TflClient client = client(null);
        // A slash in the query must not change the TfL path (no path traversal into other endpoints).
        server.expect(requestTo(containsString("/StopPoint/Search/a%2F..%2FLine%20x")))
                .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));

        client.searchStopPoints("a/../Line x", MODES, 15);

        server.verify();
    }

    @Test
    void appKeyIsAddedWhenConfiguredAndOmittedOtherwise() {
        TflClient withKey = client("secret123");
        server.expect(requestTo(containsString("app_key=secret123"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        withKey.lineStatusByModes(MODES);
        server.verify();

        TflClient anonymous = client(null);
        server.expect(requestTo(not(containsString("app_key")))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        anonymous.lineStatusByModes(MODES);
        server.verify();
    }

    @Test
    void lineStatusParsesRealTflResponseIncludingNamedOvergroundLines() throws IOException {
        TflClient client = client(null);
        server.expect(requestTo(BASE + "/Line/Mode/tube,dlr/Status"))
                .andRespond(withSuccess(fixture("line-status-2026-10-23.json"), MediaType.APPLICATION_JSON));

        List<TflLine> lines = client.lineStatusByModes(MODES);

        assertThat(lines).extracting(TflLine::id).contains("central", "elizabeth", "suffragette", "windrush");
        TflLine central = lines.stream().filter(l -> l.id().equals("central")).findFirst().orElseThrow();
        assertThat(central.lineStatuses().getFirst().statusSeverity()).isEqualTo(9);
    }

    @Test
    void retriesServerErrorsWithExponentialBackoffThenSucceeds() {
        TflClient client = client(null);
        server.expect(times(2), requestTo(containsString("/Line/Mode/"))).andRespond(withServerError());
        server.expect(once(), requestTo(containsString("/Line/Mode/"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.lineStatusByModes(MODES)).isEmpty();
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        server.verify();
    }

    @Test
    void givesUpAfterMaxAttemptsAndReportsUnavailable() {
        TflClient client = client(null);
        server.expect(times(3), requestTo(containsString("/Line/Mode/"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES))
                .isInstanceOf(TflUnavailableException.class)
                .hasMessageContaining("HTTP 502");
        assertThat(sleeps).hasSize(2);
        server.verify();
    }

    @Test
    void retriesNetworkTimeouts() {
        TflClient client = client(null);
        server.expect(times(3), requestTo(containsString("/Line/Mode/"))).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES))
                .isInstanceOf(TflUnavailableException.class)
                .hasMessageContaining("SocketTimeoutException");
    }

    @Test
    void notFoundIsNotRetried() {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString("/StopPoint/"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.searchStopPoints("x", MODES, 15)).isInstanceOf(TflNotFoundException.class);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void rateLimitIsReportedWithRetryAfterAndNotRetried() {
        TflClient client = client(null);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        server.expect(once(), requestTo(containsString("/Line/Mode/")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES))
                .isInstanceOfSatisfying(TflRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(30)));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void legacy420StatusIsTreatedAsRateLimited() {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString("/Line/Mode/"))).andRespond(withRawStatus(420));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES)).isInstanceOf(TflRateLimitedException.class);
    }

    @Test
    void badRequestExposesTflMessageWithoutRetrying() {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString("/Line/Mode/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"httpStatusCode\":400,\"message\":\"The value(s) \\\"Bogus\\\" are not valid\"}"));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES))
                .isInstanceOf(TflBadRequestException.class)
                .hasMessageContaining("Bogus");
    }

    @Test
    void authorisationFailuresAreNotRetried() {
        TflClient client = client("wrong-key");
        server.expect(once(), requestTo(containsString("/Line/Mode/"))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES))
                .isInstanceOf(TflUnavailableException.class)
                .hasMessageContaining("HTTP 403");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void unreadableResponseIsReportedAsUnavailableWithoutRetrying() {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString("/Line/Mode/"))).andRespond(withSuccess("<html>maintenance</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.lineStatusByModes(MODES)).isInstanceOf(TflUnavailableException.class);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void recordsLatencyTimerTaggedWithOutcome() {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString("/StopPoint/"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.searchStopPoints("x", MODES, 15)).isInstanceOf(TflNotFoundException.class);

        assertThat(meters.get("stepfree.tfl.requests").tag("endpoint", "stop-point-search").tag("outcome", "not_found").timer().count())
                .isEqualTo(1);
    }

    @Test
    void journeyRequestUsesTflParameterNamesAndLondonLocalTime() {
        TflClient client = client(null);
        server.expect(once(), requestTo(containsString("/Journey/JourneyResults/1000257/to/1000129")))
                .andExpect(queryParam("accessibilityPreference", "StepFreeToPlatform,NoEscalators"))
                .andExpect(queryParam("journeyPreference", "LeastWalking"))
                .andExpect(queryParam("walkingSpeed", "Slow"))
                .andExpect(queryParam("date", "20261024"))
                .andExpect(queryParam("time", "0830"))
                .andExpect(queryParam("timeIs", "Arriving"))
                .andRespond(withSuccess("{\"journeys\":[]}", MediaType.APPLICATION_JSON));

        client.journeyResults(new TflJourneyQuery("1000257", "1000129", List.of("StepFreeToPlatform", "NoEscalators"),
                "LeastWalking", "Slow", java.time.LocalDateTime.parse("2026-10-24T08:30:00"), true));

        server.verify();
    }

    @Test
    void journeyRequestOmitsUnsetOptions() {
        TflClient client = client(null);
        server.expect(once(), requestTo(BASE + "/Journey/JourneyResults/1000257/to/1000129"))
                .andRespond(withSuccess("{\"journeys\":[]}", MediaType.APPLICATION_JSON));

        client.journeyResults(new TflJourneyQuery("1000257", "1000129", List.of(), null, null, null, false));

        server.verify();
    }

    @Test
    void stopPointDisruptionsRejectsIdsThatCouldAlterThePath() {
        TflClient client = client(null);
        assertThatThrownBy(() -> client.stopPointDisruptions(List.of("940GZZLUWYP", "../Line")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.stopPointDisruptions(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redactRemovesAppKeyValues() {
        assertThat(TflClient.redact("GET https://api.tfl.gov.uk/Line?modes=tube&app_key=abc123&x=1"))
                .isEqualTo("GET https://api.tfl.gov.uk/Line?modes=tube&app_key=[REDACTED]&x=1");
        assertThat(TflClient.redact(null)).isNull();
    }
}
