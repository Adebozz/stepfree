package com.stepfreelondon.journey;

/** Walking pace TfL uses to time walking legs and interchanges. */
public enum WalkingSpeed {
    SLOW("Slow"),
    AVERAGE("Average"),
    FAST("Fast");

    private final String tflValue;

    WalkingSpeed(String tflValue) {
        this.tflValue = tflValue;
    }

    public String tflValue() {
        return tflValue;
    }
}
