package com.stepfreelondon.monitoring;

/** The TfL feed a disruption event came from. Each is read, and can fail, independently. */
public enum DisruptionSource {
    /** {@code /Line/Mode/{modes}/Status}: one event per disrupted line. */
    LINE_STATUS,
    /** {@code /StopPoint/Mode/{modes}/Disruption}: one event per notice, covering every station it names. */
    STOP_POINT,
    /** {@code /Disruptions/Lifts/v2/} (undocumented): one event per station with lifts out of service. */
    LIFT
}
