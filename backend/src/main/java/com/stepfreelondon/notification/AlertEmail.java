package com.stepfreelondon.notification;

import java.util.regex.Pattern;

/**
 * Builds the alert email. Plain text only: the content comes from TfL, and plain text can't carry
 * markup or scripts. Line breaks are stripped from the subject so TfL text can't inject headers.
 */
record AlertEmail(String subject, String body) {

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");
    private static final int MAX_SUBJECT = 150;

    static AlertEmail of(String fullName, String journeyName, String alertTitle, String message, String appBaseUrl) {
        String subject = LINE_BREAKS.matcher("StepFree London: " + alertTitle).replaceAll(" ").strip();
        if (subject.length() > MAX_SUBJECT) {
            subject = subject.substring(0, MAX_SUBJECT - 1) + "…";
        }
        String base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        String body = """
                Hello %s,

                %s
                Saved journey: %s

                %s

                See all your alerts: %s/alerts
                Turn email alerts off: %s/account

                Disruption information comes from TfL and may change. Check before you travel.
                Powered by TfL Open Data.
                """.formatted(fullName, alertTitle, journeyName, message, base, base);
        return new AlertEmail(subject, body);
    }
}
