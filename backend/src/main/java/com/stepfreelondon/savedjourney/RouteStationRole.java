package com.stepfreelondon.savedjourney;

/** Where a station sits on a saved route. Phase 4 can weigh disruptions by role (e.g. a closed interchange). */
public enum RouteStationRole {
    ORIGIN,
    INTERCHANGE,
    INTERMEDIATE,
    DESTINATION
}
