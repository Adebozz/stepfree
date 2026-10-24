package com.stepfreelondon.journey;

/**
 * Accessibility options TfL's journey planner honours. These are exactly the values in TfL's
 * swagger enum (verified 2026-10-23); nothing is simulated on our side.
 */
public enum AccessibilityPreference {
    STEP_FREE_TO_PLATFORM("StepFreeToPlatform"),
    STEP_FREE_TO_VEHICLE("StepFreeToVehicle"),
    NO_SOLID_STAIRS("NoSolidStairs"),
    NO_ESCALATORS("NoEscalators"),
    NO_ELEVATORS("NoElevators");

    private final String tflValue;

    AccessibilityPreference(String tflValue) {
        this.tflValue = tflValue;
    }

    public String tflValue() {
        return tflValue;
    }
}
