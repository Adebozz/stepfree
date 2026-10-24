package com.stepfreelondon.monitoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL for the monitoring cycle. Plain JDBC rather than JPA here: the cycle works on sets
 * (load all active events, insert-if-absent alerts), and {@code INSERT ... ON CONFLICT DO NOTHING}
 * is what makes alert creation idempotent. User-facing CRUD elsewhere stays on JPA.
 */
@Repository
class MonitoringRepository {

    /** Arbitrary constant naming the monitoring lock for {@code pg_try_advisory_xact_lock}. */
    private static final long MONITORING_LOCK = 0x5374_6570_4672_6565L; // "StepFree"

    record AlertKey(UUID journeyId, UUID eventId) {
    }

    record LastRun(Instant startedAt, Instant finishedAt, String status) {
    }

    private final JdbcClient jdbc;

    MonitoringRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- runs ---

    UUID startRun(Instant now) {
        return jdbc.sql("INSERT INTO monitoring_run (started_at, status) VALUES (:now, 'RUNNING') RETURNING id")
                .param("now", ts(now)).query(UUID.class).single();
    }

    void finishRun(UUID id, Instant now, MonitoringResult result) {
        jdbc.sql("""
                UPDATE monitoring_run SET finished_at = :now, status = :status, events_created = :created,
                       events_updated = :updated, events_resolved = :resolved, alerts_created = :alerts, error_summary = :error
                WHERE id = :id""")
                .param("now", ts(now)).param("status", result.status().name())
                .param("created", result.eventsCreated()).param("updated", result.eventsUpdated())
                .param("resolved", result.eventsResolved()).param("alerts", result.alertsCreated())
                .param("error", result.errorSummary()).param("id", id)
                .update();
    }

    Optional<LastRun> lastFinishedRun() {
        return jdbc.sql("""
                SELECT started_at, finished_at, status FROM monitoring_run
                WHERE status NOT IN ('RUNNING', 'SKIPPED') ORDER BY started_at DESC LIMIT 1""")
                .query((rs, n) -> new LastRun(instant(rs, "started_at"), instant(rs, "finished_at"), rs.getString("status")))
                .optional();
    }

    /**
     * Held until the surrounding transaction ends. A second instance (or an overlapping manual run)
     * gets {@code false} and skips the cycle instead of waiting.
     */
    boolean tryLock() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)").param("key", MONITORING_LOCK)
                .query(Boolean.class).single());
    }

    // --- events ---

    List<ActiveEvent> activeEvents() {
        Map<UUID, Set<String>> lines = scope("""
                SELECT l.event_id, l.line_id FROM disruption_event_line l
                JOIN disruption_event e ON e.id = l.event_id WHERE e.status = 'ACTIVE'""");
        Map<UUID, Set<String>> stations = scope("""
                SELECT s.event_id, s.tfl_station_id FROM disruption_event_station s
                JOIN disruption_event e ON e.id = s.event_id WHERE e.status = 'ACTIVE'""");
        return jdbc.sql("""
                SELECT id, source, identity_key, content_hash, revision, title, description
                FROM disruption_event WHERE status = 'ACTIVE'""")
                .query((rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new ActiveEvent(id, DisruptionSource.valueOf(rs.getString("source")), rs.getString("identity_key"),
                            rs.getString("content_hash"), rs.getInt("revision"), rs.getString("title"), rs.getString("description"),
                            lines.getOrDefault(id, Set.of()), stations.getOrDefault(id, Set.of()));
                })
                .list();
    }

    private Map<UUID, Set<String>> scope(String sql) {
        Map<UUID, Set<String>> result = new HashMap<>();
        jdbc.sql(sql).query(rs -> {
            result.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashSet<>()).add(rs.getString(2));
        });
        return result;
    }

    ActiveEvent insertEvent(ObservedDisruption o, Instant now) {
        UUID id = jdbc.sql("""
                INSERT INTO disruption_event (source, identity_key, content_hash, revision, category, type, severity_code,
                       severity_description, title, description, valid_from, valid_to, first_observed_at, last_observed_at, status)
                VALUES (:source, :key, :hash, 1, :category, :type, :severityCode, :severityDescription, :title, :description,
                        :validFrom, :validTo, :now, :now, 'ACTIVE')
                RETURNING id""")
                .params(eventParams(o)).param("now", ts(now))
                .query(UUID.class).single();
        insertScope(id, o);
        return new ActiveEvent(id, o.source(), o.identityKey(), o.contentHash(), 1, o.title(), o.description(), o.lineIds(), o.stationIds());
    }

    ActiveEvent updateEvent(ActiveEvent event, ObservedDisruption o, Instant now) {
        jdbc.sql("""
                UPDATE disruption_event SET content_hash = :hash, revision = revision + 1, category = :category, type = :type,
                       severity_code = :severityCode, severity_description = :severityDescription, title = :title,
                       description = :description, valid_from = :validFrom, valid_to = :validTo, last_observed_at = :now
                WHERE id = :id""")
                .params(eventParams(o)).param("now", ts(now)).param("id", event.id())
                .update();
        replaceScope(event.id(), o);
        return new ActiveEvent(event.id(), o.source(), o.identityKey(), o.contentHash(), event.revision() + 1,
                o.title(), o.description(), o.lineIds(), o.stationIds());
    }

    void touch(List<UUID> ids, Instant now) {
        if (!ids.isEmpty()) {
            jdbc.sql("UPDATE disruption_event SET last_observed_at = :now WHERE id IN (:ids)")
                    .param("now", ts(now)).param("ids", ids).update();
        }
    }

    void replaceScope(UUID id, ObservedDisruption o) {
        jdbc.sql("DELETE FROM disruption_event_line WHERE event_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM disruption_event_station WHERE event_id = :id").param("id", id).update();
        insertScope(id, o);
    }

    private void insertScope(UUID id, ObservedDisruption o) {
        for (String line : o.lineIds()) {
            jdbc.sql("INSERT INTO disruption_event_line (event_id, line_id) VALUES (:id, :line)")
                    .param("id", id).param("line", line).update();
        }
        for (String station : o.stationIds()) {
            jdbc.sql("INSERT INTO disruption_event_station (event_id, tfl_station_id) VALUES (:id, :station)")
                    .param("id", id).param("station", station).update();
        }
    }

    void resolve(List<UUID> ids, Instant now) {
        if (!ids.isEmpty()) {
            jdbc.sql("UPDATE disruption_event SET status = 'RESOLVED', resolved_at = :now WHERE id IN (:ids) AND status = 'ACTIVE'")
                    .param("now", ts(now)).param("ids", ids).update();
        }
    }

    private static Map<String, Object> eventParams(ObservedDisruption o) {
        Map<String, Object> params = new HashMap<>();
        params.put("source", o.source().name());
        params.put("key", o.identityKey());
        params.put("hash", o.contentHash());
        params.put("category", o.category());
        params.put("type", o.type());
        params.put("severityCode", o.severityCode());
        params.put("severityDescription", o.severityDescription());
        params.put("title", o.title());
        params.put("description", o.description());
        params.put("validFrom", ts(o.validFrom()));
        params.put("validTo", ts(o.validTo()));
        return params;
    }

    // --- journeys and alerts ---

    List<MonitoredJourney> monitoredJourneys() {
        String monitored = """
                FROM saved_journey sj LEFT JOIN notification_preference np ON np.user_id = sj.user_id
                WHERE sj.monitoring_enabled AND coalesce(np.monitoring_enabled, true)""";
        Map<UUID, Set<String>> lines = scope("SELECT l.saved_journey_id, l.line_id FROM saved_journey_line l "
                + "JOIN (SELECT sj.id " + monitored + ") m ON m.id = l.saved_journey_id");
        Map<UUID, Map<String, String>> stations = new HashMap<>();
        jdbc.sql("SELECT s.saved_journey_id, s.tfl_station_id, s.role FROM saved_journey_station s "
                        + "JOIN (SELECT sj.id " + monitored + ") m ON m.id = s.saved_journey_id")
                .query(rs -> {
                    stations.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getString(2), rs.getString(3));
                });
        return jdbc.sql("SELECT sj.id, sj.user_id, sj.name " + monitored)
                .query((rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new MonitoredJourney(id, rs.getObject("user_id", UUID.class), rs.getString("name"),
                            lines.getOrDefault(id, Set.of()), stations.getOrDefault(id, Map.of()));
                })
                .list();
    }

    /** (journey, event) pairs already alerted for the event's current revision, and pairs alerted at all. */
    Set<AlertKey> alerted(boolean currentRevisionOnly) {
        String sql = """
                SELECT DISTINCT a.saved_journey_id, a.disruption_event_id FROM journey_alert a
                JOIN disruption_event e ON e.id = a.disruption_event_id
                WHERE e.status = 'ACTIVE'""" + (currentRevisionOnly ? " AND a.event_revision = e.revision" : "");
        return Set.copyOf(jdbc.sql(sql)
                .query((rs, n) -> new AlertKey(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list());
    }

    /** Journeys that were told about an event (and so should hear that it ended). */
    List<UUID> journeysAlertedFor(UUID eventId) {
        return jdbc.sql("SELECT DISTINCT saved_journey_id FROM journey_alert WHERE disruption_event_id = :id AND alert_type <> 'RESOLVED'")
                .param("id", eventId).query(UUID.class).list();
    }

    /** Empty if this exact alert already exists: the unique constraint is the dedup guarantee. */
    Optional<UUID> insertAlert(UUID userId, UUID journeyId, ActiveEvent event, AlertType type, String title, String message, Instant now) {
        return jdbc.sql("""
                INSERT INTO journey_alert (user_id, saved_journey_id, disruption_event_id, event_revision, alert_type, title, message, created_at)
                VALUES (:user, :journey, :event, :revision, :type, :title, :message, :now)
                ON CONFLICT ON CONSTRAINT journey_alert_dedup DO NOTHING
                RETURNING id""")
                .param("user", userId).param("journey", journeyId).param("event", event.id())
                .param("revision", event.revision()).param("type", type.name())
                .param("title", title).param("message", message).param("now", ts(now))
                .query(UUID.class).optional();
    }

    /** Queues an email for the alert if its owner has email alerts on. Same transaction as the alert (outbox). */
    void queueEmail(UUID alertId, Instant now) {
        jdbc.sql("""
                INSERT INTO notification_delivery (journey_alert_id, channel, delivery_status, next_attempt_at)
                SELECT a.id, 'EMAIL', 'PENDING', :now FROM journey_alert a
                JOIN notification_preference p ON p.user_id = a.user_id AND p.email_enabled
                WHERE a.id = :id
                ON CONFLICT ON CONSTRAINT notification_delivery_once DO NOTHING""")
                .param("id", alertId).param("now", ts(now))
                .update();
    }

    // --- retention ---

    int deleteResolvedEventsBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM disruption_event WHERE status = 'RESOLVED' AND resolved_at < :cutoff")
                .param("cutoff", ts(cutoff)).update();
    }

    int deleteRunsBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM monitoring_run WHERE started_at < :cutoff").param("cutoff", ts(cutoff)).update();
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
