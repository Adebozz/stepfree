package com.stepfreelondon.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.stepfreelondon.notification.dto.AlertDto;
import com.stepfreelondon.notification.dto.NotificationPreferencesDto;

/** Alerts, preferences and the email outbox. Every user-facing query is scoped by {@code user_id}. */
@Repository
class NotificationRepository {

    record Claimed(UUID id, int attemptCount) {
    }

    record DeliveryDetails(UUID id, int attemptCount, String email, String fullName, boolean emailEnabled,
                           String journeyName, String title, String message) {
    }

    private final JdbcClient jdbc;

    NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- preferences ---

    NotificationPreferencesDto preferences(UUID userId) {
        return jdbc.sql("SELECT email_enabled, monitoring_enabled FROM notification_preference WHERE user_id = :user")
                .param("user", userId)
                .query((rs, n) -> new NotificationPreferencesDto(rs.getBoolean(1), rs.getBoolean(2)))
                .optional()
                .orElse(NotificationPreferencesDto.DEFAULTS);
    }

    void savePreferences(UUID userId, NotificationPreferencesDto prefs, Instant now) {
        jdbc.sql("""
                INSERT INTO notification_preference (user_id, email_enabled, monitoring_enabled, updated_at)
                VALUES (:user, :email, :monitoring, :now)
                ON CONFLICT (user_id) DO UPDATE
                SET email_enabled = EXCLUDED.email_enabled, monitoring_enabled = EXCLUDED.monitoring_enabled,
                    updated_at = EXCLUDED.updated_at""")
                .param("user", userId).param("email", prefs.emailEnabled()).param("monitoring", prefs.monitoringEnabled())
                .param("now", ts(now))
                .update();
    }

    // --- alerts ---

    List<AlertDto> alerts(UUID userId, boolean unreadOnly, int limit) {
        return jdbc.sql("SELECT a.id, a.saved_journey_id, sj.name, a.alert_type, a.title, a.message, a.created_at, a.status "
                        + "FROM journey_alert a JOIN saved_journey sj ON sj.id = a.saved_journey_id "
                        + "WHERE a.user_id = :user" + (unreadOnly ? " AND a.status = 'UNREAD'" : "")
                        + " ORDER BY a.created_at DESC, a.id LIMIT :limit")
                .param("user", userId).param("limit", limit)
                .query((rs, n) -> new AlertDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class).toInstant(),
                        "READ".equals(rs.getString(8))))
                .list();
    }

    int unreadCount(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM journey_alert WHERE user_id = :user AND status = 'UNREAD'")
                .param("user", userId).query(Integer.class).single();
    }

    /** @return false if no such alert belongs to this user */
    boolean markRead(UUID userId, UUID alertId, Instant now) {
        return jdbc.sql("""
                UPDATE journey_alert SET status = 'READ', read_at = coalesce(read_at, :now)
                WHERE id = :id AND user_id = :user""")
                .param("id", alertId).param("user", userId).param("now", ts(now))
                .update() == 1;
    }

    void markAllRead(UUID userId, Instant now) {
        jdbc.sql("UPDATE journey_alert SET status = 'READ', read_at = :now WHERE user_id = :user AND status = 'UNREAD'")
                .param("user", userId).param("now", ts(now)).update();
    }

    // --- outbox ---

    /**
     * Claims due deliveries. {@code FOR UPDATE SKIP LOCKED} lets two dispatchers run at once without
     * claiming the same row; a row stuck in SENDING (the process died mid-send) is reclaimed later.
     */
    List<Claimed> claimDue(Instant now, Instant stuckBefore, int batchSize) {
        return jdbc.sql("""
                UPDATE notification_delivery d
                SET delivery_status = 'SENDING', attempt_count = d.attempt_count + 1, last_attempt_at = :now
                WHERE d.id IN (
                    SELECT id FROM notification_delivery
                    WHERE (delivery_status = 'PENDING' AND next_attempt_at <= :now)
                       OR (delivery_status = 'SENDING' AND last_attempt_at < :stuck)
                    ORDER BY next_attempt_at
                    LIMIT :batch
                    FOR UPDATE SKIP LOCKED)
                RETURNING d.id, d.attempt_count""")
                .param("now", ts(now)).param("stuck", ts(stuckBefore)).param("batch", batchSize)
                .query((rs, n) -> new Claimed(rs.getObject(1, UUID.class), rs.getInt(2)))
                .list();
    }

    Optional<DeliveryDetails> details(Claimed claimed) {
        return jdbc.sql("""
                SELECT u.email, u.full_name, coalesce(p.email_enabled, false), sj.name, a.title, a.message
                FROM notification_delivery d
                JOIN journey_alert a ON a.id = d.journey_alert_id
                JOIN saved_journey sj ON sj.id = a.saved_journey_id
                JOIN app_user u ON u.id = a.user_id
                LEFT JOIN notification_preference p ON p.user_id = u.id
                WHERE d.id = :id""")
                .param("id", claimed.id())
                .query((rs, n) -> new DeliveryDetails(claimed.id(), claimed.attemptCount(), rs.getString(1), rs.getString(2),
                        rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6)))
                .optional();
    }

    void markSent(UUID id, Instant now) {
        jdbc.sql("UPDATE notification_delivery SET delivery_status = 'SENT', sent_at = :now, failure_reason = NULL WHERE id = :id")
                .param("id", id).param("now", ts(now)).update();
    }

    void markRetry(UUID id, Instant nextAttempt, String reason) {
        jdbc.sql("UPDATE notification_delivery SET delivery_status = 'PENDING', next_attempt_at = :next, failure_reason = :reason WHERE id = :id")
                .param("id", id).param("next", ts(nextAttempt)).param("reason", reason).update();
    }

    void markFinal(UUID id, String status, String reason) {
        jdbc.sql("UPDATE notification_delivery SET delivery_status = :status, failure_reason = :reason WHERE id = :id")
                .param("id", id).param("status", status).param("reason", reason).update();
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
