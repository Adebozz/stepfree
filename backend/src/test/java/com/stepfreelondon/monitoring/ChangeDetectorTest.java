package com.stepfreelondon.monitoring;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeDetectorTest {

    @Test
    void newKeyIsCreated() {
        var changes = ChangeDetector.diff(List.of(), snapshot(Map.of(DisruptionSource.LINE_STATUS, List.of(observed("central", "h1")))));

        assertThat(changes.created()).extracting(ObservedDisruption::identityKey).containsExactly("central");
        assertThat(changes.updated()).isEmpty();
        assertThat(changes.resolved()).isEmpty();
    }

    @Test
    void sameKeyAndHashIsUnchangedAndDifferentHashIsUpdated() {
        ActiveEvent central = active("central", "h1");
        ActiveEvent victoria = active("victoria", "h1");

        var changes = ChangeDetector.diff(List.of(central, victoria), snapshot(Map.of(DisruptionSource.LINE_STATUS,
                List.of(observed("central", "h1"), observed("victoria", "h2")))));

        assertThat(changes.unchanged()).extracting(u -> u.event().id()).containsExactly(central.id());
        assertThat(changes.updated()).extracting(u -> u.event().id()).containsExactly(victoria.id());
        assertThat(changes.created()).isEmpty();
        assertThat(changes.resolved()).isEmpty();
    }

    @Test
    void keyMissingFromASuccessfulReadIsResolved() {
        ActiveEvent central = active("central", "h1");

        var changes = ChangeDetector.diff(List.of(central), snapshot(Map.of(DisruptionSource.LINE_STATUS, List.of())));

        assertThat(changes.resolved()).containsExactly(central);
    }

    @Test
    void eventsOfAFeedThatCouldNotBeReadAreLeftAlone() {
        ActiveEvent lift = new ActiveEvent(UUID.randomUUID(), DisruptionSource.LIFT, "940GZZLUWYP", "h", 1, "Lift", "d",
                Set.of(), Set.of("940GZZLUWYP"));

        // Only line status was read: the lift feed failed, so its event must not be resolved.
        var changes = ChangeDetector.diff(List.of(lift), snapshot(Map.of(DisruptionSource.LINE_STATUS, List.of())));

        assertThat(changes.resolved()).isEmpty();
        assertThat(changes.unchanged()).isEmpty();
    }

    @Test
    void sameKeyInDifferentSourcesAreDifferentEvents() {
        ActiveEvent line = active("940GZZLUWYP", "h");
        var lift = new ObservedDisruption(DisruptionSource.LIFT, "940GZZLUWYP", "h", null, null, null, null, "t", "d", null, null,
                Set.of(), Set.of("940GZZLUWYP"));

        var changes = ChangeDetector.diff(List.of(line), snapshot(Map.of(
                DisruptionSource.LINE_STATUS, List.of(observed("940GZZLUWYP", "h")), DisruptionSource.LIFT, List.of(lift))));

        assertThat(changes.created()).containsExactly(lift);
        assertThat(changes.unchanged()).hasSize(1);
    }

    @Test
    void duplicateKeysInOneReadCountOnce() {
        var changes = ChangeDetector.diff(List.of(), snapshot(Map.of(DisruptionSource.LINE_STATUS,
                List.of(observed("central", "h1"), observed("central", "h2")))));

        assertThat(changes.created()).hasSize(1).first().extracting(ObservedDisruption::contentHash).isEqualTo("h1");
    }

    @Test
    void scopeChangeIsDetectedWithoutBeingAnUpdate() {
        ActiveEvent event = active("central", "h1");
        var widened = new ObservedDisruption(DisruptionSource.LINE_STATUS, "central", "h1", null, null, null, null, "t", "d",
                null, null, Set.of("central"), Set.of("940GZZLUBNK"));

        var changes = ChangeDetector.diff(List.of(event), snapshot(Map.of(DisruptionSource.LINE_STATUS, List.of(widened))));

        assertThat(changes.updated()).isEmpty();
        assertThat(changes.unchanged()).singleElement().satisfies(u -> assertThat(changes.scopeChanged(u)).isTrue());
    }

    static ActiveEvent active(String key, String hash) {
        return new ActiveEvent(UUID.randomUUID(), DisruptionSource.LINE_STATUS, key, hash, 1, "t", "d", Set.of(key), Set.of());
    }

    static ObservedDisruption observed(String key, String hash) {
        return new ObservedDisruption(DisruptionSource.LINE_STATUS, key, hash, null, null, null, null, "t", "d", null, null,
                Set.of(key), Set.of());
    }

    static FeedSnapshot snapshot(Map<DisruptionSource, List<ObservedDisruption>> observed) {
        return new FeedSnapshot(observed, List.of());
    }
}
