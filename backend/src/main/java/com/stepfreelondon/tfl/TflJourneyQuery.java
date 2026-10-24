package com.stepfreelondon.tfl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Parameters for a TfL journey search, already in TfL's vocabulary.
 *
 * @param fromIcs                  origin ICS code
 * @param toIcs                    destination ICS code
 * @param accessibilityPreferences TfL enum values, e.g. {@code StepFreeToPlatform}
 * @param journeyPreference        {@code LeastTime}, {@code LeastInterchange} or {@code LeastWalking}
 * @param walkingSpeed             {@code Slow}, {@code Average} or {@code Fast}
 * @param time                     London local time, or null for "now"
 * @param arriveBy                 true if {@code time} is an arrival time
 */
public record TflJourneyQuery(String fromIcs, String toIcs, List<String> accessibilityPreferences,
                              String journeyPreference, String walkingSpeed, LocalDateTime time, boolean arriveBy) {
}
