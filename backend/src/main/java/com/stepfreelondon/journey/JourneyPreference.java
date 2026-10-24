package com.stepfreelondon.journey;

/** How TfL should rank journeys. */
public enum JourneyPreference {
    LEAST_TIME("LeastTime"),
    LEAST_INTERCHANGE("LeastInterchange"),
    LEAST_WALKING("LeastWalking");

    private final String tflValue;

    JourneyPreference(String tflValue) {
        this.tflValue = tflValue;
    }

    public String tflValue() {
        return tflValue;
    }
}
