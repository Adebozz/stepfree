package com.stepfreelondon.savedjourney;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One row of {@code saved_journey_station}: a value object owned by its saved journey. */
@Embeddable
class RouteStation {

    @Column(name = "tfl_station_id", nullable = false)
    private String tflStationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteStationRole role;

    protected RouteStation() {
        // for JPA
    }

    RouteStation(String tflStationId, RouteStationRole role) {
        this.tflStationId = tflStationId;
        this.role = role;
    }

    String getTflStationId() {
        return tflStationId;
    }

    RouteStationRole getRole() {
        return role;
    }
}
