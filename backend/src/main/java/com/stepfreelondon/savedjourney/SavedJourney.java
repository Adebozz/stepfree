package com.stepfreelondon.savedjourney;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A journey a user saved, with the route (lines and stations) of the option they chose. The route
 * is fixed at save time; only the name and monitoring flag can change.
 *
 * <p>The owner is a plain {@code userId} column rather than a {@code @ManyToOne AppUser}: this
 * package doesn't need the user entity, and keeping it out keeps the modules independent.
 */
@Entity
@Table(name = "saved_journey")
class SavedJourney {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_station_id", nullable = false, updatable = false)
    private Station origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_station_id", nullable = false, updatable = false)
    private Station destination;

    /** Stored as enum names (e.g. {@code STEP_FREE_TO_PLATFORM}), not TfL's values, so TfL renames don't touch the data. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> accessibilityPreferences;

    private String journeyPreference;

    @Column(nullable = false)
    private boolean monitoringEnabled;

    @ElementCollection
    @CollectionTable(name = "saved_journey_line", joinColumns = @JoinColumn(name = "saved_journey_id"))
    @OrderColumn(name = "position")
    @Column(name = "line_id", nullable = false)
    private List<String> lineIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "saved_journey_station", joinColumns = @JoinColumn(name = "saved_journey_id"))
    @OrderColumn(name = "position")
    private List<RouteStation> stations = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SavedJourney() {
        // for JPA
    }

    SavedJourney(UUID userId, String name, Station origin, Station destination, List<String> accessibilityPreferences,
                 String journeyPreference, List<String> lineIds, List<RouteStation> stations, Instant now) {
        this.userId = userId;
        this.name = name;
        this.origin = origin;
        this.destination = destination;
        this.accessibilityPreferences = List.copyOf(accessibilityPreferences);
        this.journeyPreference = journeyPreference;
        this.monitoringEnabled = true;
        this.lineIds = new ArrayList<>(lineIds);
        this.stations = new ArrayList<>(stations);
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Station getOrigin() {
        return origin;
    }

    Station getDestination() {
        return destination;
    }

    List<String> getAccessibilityPreferences() {
        return accessibilityPreferences;
    }

    String getJourneyPreference() {
        return journeyPreference;
    }

    boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    List<String> getLineIds() {
        return lineIds;
    }

    List<RouteStation> getStations() {
        return stations;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    void setMonitoringEnabled(boolean monitoringEnabled, Instant now) {
        this.monitoringEnabled = monitoringEnabled;
        this.updatedAt = now;
    }
}
