package com.stepfreelondon.monitoring;

/**
 * Alert wording. Says what TfL reports, never more: a resolved event is "no longer reported by TfL",
 * not "fixed", because the feed going quiet is all we know.
 */
final class AlertMessages {

    private AlertMessages() {
    }

    static String title(AlertType type, ActiveEvent event) {
        return switch (type) {
            case NEW -> event.title();
            case UPDATED -> "Update: " + event.title();
            case RESOLVED -> "No longer reported: " + event.title();
        };
    }

    static String message(AlertType type, ActiveEvent event) {
        return switch (type) {
            case NEW, UPDATED -> event.description();
            case RESOLVED -> "TfL no longer reports this disruption. It previously said: " + event.description();
        };
    }
}
