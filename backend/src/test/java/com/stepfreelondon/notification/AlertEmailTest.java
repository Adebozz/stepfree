package com.stepfreelondon.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEmailTest {

    @Test
    void bodyNamesTheJourneyAndLinksBackToTheApp() {
        AlertEmail email = AlertEmail.of("Ada", "Home to work", "Metropolitan: Minor Delays",
                "Minor delays due to a signal failure.", "https://stepfree.example/");

        assertThat(email.subject()).isEqualTo("StepFree London: Metropolitan: Minor Delays");
        assertThat(email.body())
                .contains("Hello Ada,", "Saved journey: Home to work", "Minor delays due to a signal failure.",
                        "https://stepfree.example/alerts", "https://stepfree.example/account", "Powered by TfL Open Data")
                .doesNotContain("example//");
    }

    @Test
    void textFromTflCannotAddHeadersOrOverlongSubjects() {
        AlertEmail injected = AlertEmail.of("Ada", "j", "Delays\r\nBcc: victim@example.com", "m", "http://localhost");
        assertThat(injected.subject()).doesNotContain("\r", "\n").isEqualTo("StepFree London: Delays Bcc: victim@example.com");

        AlertEmail longOne = AlertEmail.of("Ada", "j", "x".repeat(500), "m", "http://localhost");
        assertThat(longOne.subject()).hasSize(150).endsWith("…");
    }
}
