package com.stepfreelondon.savedjourney;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A TfL station referenced by at least one saved journey. Not a copy of the whole network. */
@Entity
@Table(name = "station")
class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tflStationId;

    private String icsCode;

    @Column(nullable = false)
    private String name;

    private Double latitude;

    private Double longitude;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> modes;

    private String zone;

    @Column(nullable = false)
    private Instant lastSyncedAt;

    protected Station() {
        // for JPA
    }

    Station(String tflStationId, String icsCode, String name, Double latitude, Double longitude, List<String> modes,
            Instant syncedAt) {
        this.tflStationId = tflStationId;
        this.icsCode = icsCode;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.modes = List.copyOf(modes);
        this.lastSyncedAt = syncedAt;
    }

    String getTflStationId() {
        return tflStationId;
    }

    String getIcsCode() {
        return icsCode;
    }

    String getName() {
        return name;
    }
}
