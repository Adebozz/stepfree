package com.stepfreelondon.savedjourney;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code stepfree.saved-journeys}. The cap bounds the monitoring work per user in Phase 4. */
@ConfigurationProperties("stepfree.saved-journeys")
record SavedJourneyProperties(int maxPerUser) {
}
