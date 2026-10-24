package com.stepfreelondon.monitoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stepfreelondon.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/** The wake-up endpoint: no session or CSRF, only the shared token. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stepfree.monitoring.trigger-token=test-trigger-token-0123456789")
class MonitoringTriggerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private DisruptionFeed feed;

    @Test
    void rightTokenRunsACycle() throws Exception {
        given(feed.read()).willReturn(new FeedSnapshot(Map.of(DisruptionSource.LINE_STATUS, List.of()), List.of()));

        HttpResponse<String> response = post("test-trigger-token-0123456789");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"SUCCEEDED\"");
    }

    @Test
    void missingOrWrongTokenIsRejectedWithoutRunning() throws Exception {
        assertThat(post(null).statusCode()).isEqualTo(401);
        assertThat(post("test-trigger-token-012345678").statusCode()).isEqualTo(401);
        assertThat(post("x").statusCode()).isEqualTo(401);
        verifyNoInteractions(feed);
    }

    private HttpResponse<String> post(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/monitoring/run"))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header(MonitoringTriggerController.TOKEN_HEADER, token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
