package com.stepfreelondon;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A minimal browser stand-in for integration tests: real HTTP, a cookie jar, and the SPA CSRF
 * pattern (read the {@code XSRF-TOKEN} cookie, echo it as {@code X-XSRF-TOKEN}). Each instance is
 * one "browser", so two instances are two independent sessions.
 */
public final class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public record Response(int status, HttpHeaders headers, JsonNode body, String text) {

        public Response(int status, HttpHeaders headers, JsonNode body) {
            this(status, headers, body, null);
        }


        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

        public List<String> setCookies() {
            return headers.allValues("Set-Cookie");
        }
    }

    public Response get(String path) {
        return send("GET", path, null, true);
    }

    /** A GET that doesn't parse the body as JSON (HTML pages, static files). */
    public Response getRaw(String path) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers(), null, response.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public Response post(String path, Object body) {
        return send("POST", path, body, true);
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body, true);
    }

    public Response put(String path, Object body) {
        return send("PUT", path, body, true);
    }

    public Response delete(String path, Object body) {
        return send("DELETE", path, body, true);
    }

    /** As a cross-site attacker would: the browser sends our cookies, but the page can't read the CSRF token. */
    public Response postWithoutCsrf(String path, Object body) {
        return send("POST", path, body, false);
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst();
    }

    private Response send(String method, String path, Object body, boolean withCsrf) {
        if (withCsrf && !method.equals("GET") && cookie("XSRF-TOKEN").isEmpty()) {
            get("/api/v1/auth/csrf");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (withCsrf) {
            cookie("XSRF-TOKEN").ifPresent(token -> request.header("X-XSRF-TOKEN", token));
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body().isBlank() ? null : JSON.readTree(response.body());
            return new Response(response.statusCode(), response.headers(), json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
