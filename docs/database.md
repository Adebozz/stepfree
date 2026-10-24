# Database

PostgreSQL 17, schema managed only by Flyway migrations in `backend/src/main/resources/db/migration`. Hibernate runs with `ddl-auto=validate`, so it checks the schema and never changes it.

## Status

| Migration | Contents | Phase |
|---|---|---|
| `V1__users_and_sessions` | `app_user`, `spring_session`, `spring_session_attributes` | 3 ✅ |
| `V2__saved_journeys` | `station`, `saved_journey`, `saved_journey_line`, `saved_journey_station` | 3 ✅ |
| planned `V3` | disruption, monitoring, alerts, notifications | 4 |

## Proposed schema (refined from the original requirements)

Changes from the original proposal, and why:

- `user` → **`app_user`** (`user` is a reserved word in PostgreSQL).
- Email: **`text` + `CHECK (email = lower(email))`** instead of `citext` (changed in Phase 3). The application lower-cases emails, the CHECK makes the database enforce it, so the plain `UNIQUE` is case-insensitive. Hibernate's `ddl-auto=validate` doesn't understand `citext`, and this needs no extension.
- Spring Session tables are created **by Flyway** (copied from `spring-session-jdbc` 4.1.1's `schema-postgresql.sql`), with `spring.session.jdbc.initialize-schema=never`. `principal_name` holds the **user id**, so a user's sessions can be found and deleted (password change, account deletion).
- Route tables have a **`position`** column so lines and stops keep travel order.
- `station` gets **`ics_code`**: TfL's journey planner needs it for hub stations (see `feasibility.md` §1.2). It stores only stations referenced by saved journeys, not the whole network.
- `saved_journey` gets **child tables for the chosen route** (`saved_journey_line`, `saved_journey_station`). This is what makes "is this journey affected?" answerable without guessing (risk R6).
- `disruption_event` uses **`identity_key` + `content_hash`** instead of a single fingerprint, because TfL supplies no ids (§1.8). The key says *which* disruption it is; the hash says whether its content changed.
- **`monitoring_run`** added to record the outcome of each scheduler cycle ("record monitoring outcomes").
- `notification_preference` keyed by `user_id` (strictly one row per user).
- Dedup constraints: `journey_alert` unique on `(saved_journey_id, disruption_event_id, alert_type, event_revision)`; `notification_delivery` unique on `(journey_alert_id, channel)`.

```
app_user(id uuid pk, full_name, email text unique check(lower), password_hash, enabled, created_at, updated_at)

station(id uuid pk, tfl_station_id text unique, ics_code text, name, latitude, longitude,
        modes text[], zone text, last_synced_at)

saved_journey(id uuid pk, user_id fk→app_user ON DELETE CASCADE, name,
              origin_station_id fk→station, destination_station_id fk→station,
              accessibility_preferences text[], journey_preference text,
              monitoring_enabled bool, created_at, updated_at,
              unique(user_id, name))
saved_journey_line(saved_journey_id fk CASCADE, line_id text, position int, pk(saved_journey_id, line_id))
saved_journey_station(saved_journey_id fk CASCADE, tfl_station_id text, role text, position int,  -- ORIGIN|INTERCHANGE|DESTINATION|INTERMEDIATE
                      pk(saved_journey_id, tfl_station_id))

disruption_event(id uuid pk, source text,             -- LINE_STATUS|STOP_POINT|LIFT
                 identity_key text, content_hash text, revision int,
                 category, type, severity_code int null, severity_description,
                 description, valid_from, valid_to,
                 first_observed_at, last_observed_at, resolved_at, status,  -- ACTIVE|RESOLVED
                 unique(source, identity_key) where status='ACTIVE')
disruption_event_line(event_id fk CASCADE, line_id, pk)
disruption_event_station(event_id fk CASCADE, tfl_station_id, pk)

monitoring_run(id uuid pk, started_at, finished_at, status, events_created, events_updated,
               events_resolved, alerts_created, error_summary)

journey_alert(id uuid pk, user_id fk CASCADE, saved_journey_id fk CASCADE,
              disruption_event_id fk, event_revision int, alert_type, message,
              status, created_at, read_at,
              unique(saved_journey_id, disruption_event_id, alert_type, event_revision))

notification_preference(user_id pk fk CASCADE, email_enabled bool default false,
                        monitoring_enabled bool default true, updated_at)

notification_delivery(id uuid pk, journey_alert_id fk CASCADE, channel, delivery_status,
                      attempt_count, last_attempt_at, sent_at, failure_reason,
                      unique(journey_alert_id, channel))
```

Indexes planned for: `saved_journey(user_id)`, `saved_journey_line(line_id)`, `saved_journey_station(tfl_station_id)`, `journey_alert(user_id, status, created_at desc)`, `disruption_event(status)`.

## Deletion and retention

Deleting an account (password required) hard-deletes the `app_user` row. `ON DELETE CASCADE` removes saved journeys and their routes (and, from Phase 4, alerts, deliveries and preferences). The user's sessions are deleted through Spring Session's principal-name index; that step is in the application because `spring_session.principal_name` can't be a foreign key. Verified by `AuthIntegrationTest` and `SavedJourneyIntegrationTest`. Disruption events contain no personal data and are kept. Resolved events older than 90 days are eligible for cleanup (Phase 4).
