package com.stepfreelondon.monitoring;

public enum RunStatus {
    RUNNING,
    /** Every feed was read and applied. */
    SUCCEEDED,
    /** Some feeds failed; the others were applied, and events from the failed ones were left untouched. */
    PARTIAL,
    /** Nothing was applied. */
    FAILED,
    /** Another cycle held the lock. */
    SKIPPED
}
