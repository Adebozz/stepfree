-- Saved journeys and the route they were saved with (Phase 3).

-- Only stations referenced by saved journeys, not the whole network. ics_code is what TfL's
-- journey planner needs to re-plan the journey (feasibility.md §1.2).
CREATE TABLE station (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tfl_station_id text        NOT NULL UNIQUE,
    ics_code       text,
    name           text        NOT NULL,
    latitude       double precision,
    longitude      double precision,
    modes          text[]      NOT NULL DEFAULT '{}',
    zone           text,
    last_synced_at timestamptz NOT NULL
);

CREATE TABLE saved_journey (
    id                        uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name                      text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    origin_station_id         uuid        NOT NULL REFERENCES station (id),
    destination_station_id    uuid        NOT NULL REFERENCES station (id),
    accessibility_preferences text[]      NOT NULL DEFAULT '{}',
    journey_preference        text,
    monitoring_enabled        boolean     NOT NULL DEFAULT true,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT saved_journey_name_per_user UNIQUE (user_id, name)
);

CREATE INDEX saved_journey_user_idx ON saved_journey (user_id);

-- The lines and stations of the route the user chose. Phase 4 matches disruptions against these,
-- which is what makes "is this journey affected?" answerable without guessing.
CREATE TABLE saved_journey_line (
    saved_journey_id uuid     NOT NULL REFERENCES saved_journey (id) ON DELETE CASCADE,
    line_id          text     NOT NULL,
    position         integer  NOT NULL,
    PRIMARY KEY (saved_journey_id, line_id)
);

CREATE INDEX saved_journey_line_line_idx ON saved_journey_line (line_id);

CREATE TABLE saved_journey_station (
    saved_journey_id uuid     NOT NULL REFERENCES saved_journey (id) ON DELETE CASCADE,
    tfl_station_id   text     NOT NULL,
    role             text     NOT NULL CHECK (role IN ('ORIGIN', 'INTERCHANGE', 'DESTINATION', 'INTERMEDIATE')),
    position         integer  NOT NULL,
    PRIMARY KEY (saved_journey_id, tfl_station_id)
);

CREATE INDEX saved_journey_station_station_idx ON saved_journey_station (tfl_station_id);
