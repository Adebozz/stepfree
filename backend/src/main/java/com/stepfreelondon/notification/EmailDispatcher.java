package com.stepfreelondon.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.stepfreelondon.notification.NotificationRepository.Claimed;
import com.stepfreelondon.notification.NotificationRepository.DeliveryDetails;

/**
 * Sends queued alert emails (the outbox written by the monitoring cycle).
 *
 * <p>Rows are claimed in a short statement, sent with no transaction open, then marked. A failed send
 * is retried after each {@code retry-backoff} step, then marked FAILED. Delivery is at-least-once: if
 * the process dies after SMTP accepted a message but before it was marked SENT, it is sent again.
 * That is the usual trade-off; the alternative (mark first) can lose emails.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    private static final int MAX_REASON = 300;

    private final NotificationRepository repository;
    private final EmailSender sender;
    private final NotificationProperties properties;
    private final Clock clock;

    EmailDispatcher(NotificationRepository repository, EmailSender sender, NotificationProperties properties, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return number of emails sent */
    public int dispatchDue() {
        Instant now = clock.instant();
        List<Claimed> claimed = repository.claimDue(now, now.minus(properties.stuckAfter()), properties.batchSize());
        int sent = 0;
        for (Claimed delivery : claimed) {
            if (deliver(delivery)) {
                sent++;
            }
        }
        if (!claimed.isEmpty()) {
            log.info("Email dispatch: {} of {} sent", sent, claimed.size());
        }
        return sent;
    }

    private boolean deliver(Claimed claimed) {
        Optional<DeliveryDetails> found = repository.details(claimed);
        if (found.isEmpty()) {
            return false; // alert, journey or user deleted since: the row went with them
        }
        DeliveryDetails d = found.get();
        if (!d.emailEnabled()) {
            repository.markFinal(d.id(), "CANCELLED", "Email alerts were turned off before sending");
            return false;
        }
        AlertEmail email = AlertEmail.of(d.fullName(), d.journeyName(), d.title(), d.message(), properties.appBaseUrl());
        try {
            sender.send(d.email(), email.subject(), email.body());
            repository.markSent(d.id(), clock.instant());
            return true;
        } catch (RuntimeException e) {
            // Exception messages from SMTP can echo addresses; store and log the type only.
            String reason = truncate(e.getClass().getSimpleName());
            if (d.attemptCount() >= properties.maxAttempts()) {
                log.warn("Email delivery {} failed permanently after {} attempts: {}", d.id(), d.attemptCount(), reason);
                repository.markFinal(d.id(), "FAILED", reason);
            } else {
                Instant next = clock.instant().plus(properties.retryBackoff().get(d.attemptCount() - 1));
                log.info("Email delivery {} failed (attempt {}): {}; retrying at {}", d.id(), d.attemptCount(), reason, next);
                repository.markRetry(d.id(), next, reason);
            }
            return false;
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_REASON ? text : text.substring(0, MAX_REASON);
    }
}
