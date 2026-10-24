package com.stepfreelondon.tfl;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.stepfreelondon.tfl.model.TflApiError;
import com.stepfreelondon.tfl.model.TflDisruptedPoint;
import com.stepfreelondon.tfl.model.TflJourneyResponse;
import com.stepfreelondon.tfl.model.TflLiftDisruption;
import com.stepfreelondon.tfl.model.TflLine;
import com.stepfreelondon.tfl.model.TflStopPoint;
import com.stepfreelondon.tfl.model.TflStopPointSearchResponse;

/**
 * The only class that talks to the TfL Unified API.
 *
 * <p>Every call goes through {@link #get}, which applies the same policy: bounded retries with
 * exponential backoff for server errors and network failures only, translation of HTTP errors into
 * {@link TflException} subtypes, and a latency timer ({@code stepfree.tfl.requests}) tagged by
 * endpoint and outcome. Rate-limit responses are not retried here; waiting would tie up a request
 * thread, and callers can fall back to cached data instead.
 */
public class TflClient {

    private static final Logger log = LoggerFactory.getLogger(TflClient.class);
    private static final Pattern APP_KEY = Pattern.compile("(?i)(app_key=)[^&\\s\"]+");
    private static final Pattern STOP_ID = Pattern.compile("^[A-Za-z0-9]{1,32}$");
    private static final DateTimeFormatter TFL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TFL_TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final TflProperties properties;
    private final MeterRegistry meterRegistry;
    private final Sleeper sleeper;

    public TflClient(RestClient.Builder builder, TflProperties properties, MeterRegistry meterRegistry, Sleeper sleeper) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.sleeper = sleeper;
        builder.baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "StepFreeLondon/0.1");
        if (properties.hasAppKey()) {
            builder.requestInterceptor(appKeyInterceptor(properties.appKey()));
        }
        this.restClient = builder.build();
    }

    /** Searches stations by name. Hubs are included so multi-mode interchanges appear once. */
    public TflStopPointSearchResponse searchStopPoints(String query, List<String> modes, int maxResults) {
        return get("stop-point-search", client -> client.get()
                .uri(uri -> uri.path("/StopPoint/Search/{query}")
                        .queryParam("modes", String.join(",", modes))
                        .queryParam("maxResults", maxResults)
                        .queryParam("includeHubs", true)
                        .build(query))
                .retrieve()
                .body(TflStopPointSearchResponse.class));
    }

    /**
     * Current status of every line for the given modes. Modes are internal constants, so they go
     * into the path template directly: a URI variable would percent-encode the commas as %2C.
     */
    public List<TflLine> lineStatusByModes(List<String> modes) {
        return get("line-status", client -> client.get()
                .uri("/Line/Mode/" + String.join(",", modes) + "/Status")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TflLine>>() { }));
    }

    /**
     * Plans journeys between two ICS codes. TfL answers 404 when no journey exists for the inputs,
     * which surfaces as {@link TflNotFoundException}.
     */
    public TflJourneyResponse journeyResults(TflJourneyQuery query) {
        return get("journey-results", client -> client.get()
                .uri(uri -> {
                    uri.path("/Journey/JourneyResults/{from}/to/{to}");
                    if (!query.accessibilityPreferences().isEmpty()) {
                        uri.queryParam("accessibilityPreference", String.join(",", query.accessibilityPreferences()));
                    }
                    if (query.journeyPreference() != null) {
                        uri.queryParam("journeyPreference", query.journeyPreference());
                    }
                    if (query.walkingSpeed() != null) {
                        uri.queryParam("walkingSpeed", query.walkingSpeed());
                    }
                    if (query.time() != null) {
                        uri.queryParam("date", query.time().format(TFL_DATE))
                                .queryParam("time", query.time().format(TFL_TIME))
                                .queryParam("timeIs", query.arriveBy() ? "Arriving" : "Departing");
                    }
                    return uri.build(query.fromIcs(), query.toIcs());
                })
                .retrieve()
                .body(TflJourneyResponse.class));
    }

    /** A station or hub with its properties and children. */
    public TflStopPoint stopPoint(String id) {
        return get("stop-point", client -> client.get()
                .uri("/StopPoint/{id}", id)
                .retrieve()
                .body(TflStopPoint.class));
    }

    /** Current and planned disruptions at the given stations. Ids are TfL NaPTAN ids. */
    public List<TflDisruptedPoint> stopPointDisruptions(List<String> ids) {
        // Ids are joined into the path unencoded (TfL expects literal commas), so they must be plain identifiers.
        if (ids.isEmpty() || !ids.stream().allMatch(id -> STOP_ID.matcher(id).matches())) {
            throw new IllegalArgumentException("Invalid stop point ids");
        }
        return get("stop-point-disruptions", client -> client.get()
                .uri("/StopPoint/" + String.join(",", ids) + "/Disruption")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TflDisruptedPoint>>() { }));
    }

    /** All station disruptions for the given modes. */
    public List<TflDisruptedPoint> stopPointDisruptionsByModes(List<String> modes) {
        return get("mode-stop-point-disruptions", client -> client.get()
                .uri("/StopPoint/Mode/" + String.join(",", modes) + "/Disruption")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TflDisruptedPoint>>() { }));
    }

    /** Reported lift outages across the network. Undocumented TfL endpoint; may disappear. */
    public List<TflLiftDisruption> liftDisruptions() {
        return get("lift-disruptions", client -> client.get()
                .uri("/Disruptions/Lifts/v2/")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TflLiftDisruption>>() { }));
    }

    private <T> T get(String endpoint, Function<RestClient, T> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return executeWithRetry(endpoint, call);
        } catch (TflException e) {
            outcome = switch (e) {
                case TflNotFoundException ignored -> "not_found";
                case TflBadRequestException ignored -> "bad_request";
                case TflRateLimitedException ignored -> "rate_limited";
                case TflUnavailableException ignored -> "unavailable";
            };
            throw e;
        } finally {
            sample.stop(Timer.builder("stepfree.tfl.requests")
                    .description("Latency of calls to the TfL Unified API")
                    .tag("endpoint", endpoint)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    private <T> T executeWithRetry(String endpoint, Function<RestClient, T> call) {
        int maxAttempts = properties.maxAttempts();
        for (int attempt = 1; ; attempt++) {
            String failure;
            try {
                T body = call.apply(restClient);
                if (body == null) {
                    throw new TflUnavailableException(endpoint, "TfL returned an empty response");
                }
                return body;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                switch (status) {
                    case 404 -> throw new TflNotFoundException(endpoint, "TfL does not recognise the requested resource");
                    case 400 -> throw new TflBadRequestException(endpoint, errorMessage(e));
                    case 420, 429 -> {
                        log.warn("TfL rate limit hit on {}", endpoint);
                        throw new TflRateLimitedException(endpoint, retryAfter(e));
                    }
                    default -> {
                        if (status < 500) {
                            // 401/403 etc. are configuration problems (e.g. an invalid app key): retrying won't help.
                            log.error("TfL rejected {} with HTTP {}; check TFL_APP_KEY", endpoint, status);
                            throw new TflUnavailableException(endpoint, "TfL rejected the request (HTTP " + status + ")");
                        }
                        failure = "HTTP " + status;
                    }
                }
            } catch (ResourceAccessException e) {
                // The exception message contains the full URL (and app key), so only the cause type is logged.
                failure = "network error (" + rootCauseName(e) + ")";
            } catch (RestClientException e) {
                log.error("Unreadable TfL response from {}: {}", endpoint, redact(e.getMessage()));
                throw new TflUnavailableException(endpoint, "TfL returned a response we could not read");
            }

            if (attempt >= maxAttempts) {
                log.warn("TfL {} failed after {} attempt(s): {}", endpoint, attempt, failure);
                throw new TflUnavailableException(endpoint, "TfL is currently unavailable: " + failure);
            }
            Duration backoff = properties.initialBackoff().multipliedBy(1L << (attempt - 1));
            log.info("TfL {} failed ({}); retrying in {} ms (attempt {} of {})",
                    endpoint, failure, backoff.toMillis(), attempt + 1, maxAttempts);
            pause(endpoint, backoff);
        }
    }

    private void pause(String endpoint, Duration backoff) {
        try {
            sleeper.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TflUnavailableException(endpoint, "Interrupted while retrying TfL request");
        }
    }

    private static String errorMessage(RestClientResponseException e) {
        try {
            TflApiError error = e.getResponseBodyAs(TflApiError.class);
            if (error != null && error.message() != null) {
                return redact(error.message());
            }
        } catch (RuntimeException parseFailure) {
            log.debug("Could not parse TfL error body: {}", parseFailure.getClass().getSimpleName());
        }
        return "TfL rejected the request parameters";
    }

    private static Duration retryAfter(RestClientResponseException e) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException notSeconds) {
            return null; // HTTP-date form; not worth parsing for TfL
        }
    }

    private static String rootCauseName(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    /** Removes {@code app_key} values from text that may echo a request URL. */
    static String redact(String text) {
        return text == null ? null : APP_KEY.matcher(text).replaceAll("$1[REDACTED]");
    }

    static ClientHttpRequestInterceptor appKeyInterceptor(String appKey) {
        return (request, body, execution) -> {
            var uri = UriComponentsBuilder.fromUri(request.getURI())
                    .queryParam("app_key", appKey)
                    .build(true)
                    .toUri();
            return execution.execute(new HttpRequestWrapper(request) {
                @Override
                public java.net.URI getURI() {
                    return uri;
                }
            }, body);
        };
    }
}
