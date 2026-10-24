package com.stepfreelondon.monitoring;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.stepfreelondon.monitoring.MonitoringRepository.AlertKey;
import com.stepfreelondon.monitoring.MonitoringRepository.LastRun;

/**
 * One monitoring cycle: read TfL, record what changed, alert affected journeys.
 *
 * <ol>
 *   <li>Read all feeds <em>outside</em> a transaction (TfL can take seconds; no connection is held).</li>
 *   <li>In one short transaction, take a Postgres advisory lock (so only one instance applies a cycle),
 *       diff against the active events, write the changes, and insert alerts plus their email outbox rows.</li>
 *   <li>Record the outcome in {@code monitoring_run}.</li>
 * </ol>
 * Overlap inside one instance is prevented by an {@link AtomicBoolean}; across instances by the lock;
 * and if both somehow failed, the unique constraint on {@code journey_alert} still prevents a duplicate alert.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final DisruptionFeed feed;
    private final MonitoringRepository repository;
    private final TransactionTemplate transactions;
    private final MonitoringProperties properties;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    MonitoringService(DisruptionFeed feed, MonitoringRepository repository, TransactionTemplate transactions,
                      MonitoringProperties properties, Clock clock) {
        this.feed = feed;
        this.repository = repository;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    public MonitoringResult runCycle() {
        if (!running.compareAndSet(false, true)) {
            return MonitoringResult.skipped("A cycle is already running in this instance");
        }
        try {
            UUID runId = repository.startRun(clock.instant());
            MonitoringResult result = execute();
            repository.finishRun(runId, clock.instant(), result);
            log.info("Monitoring cycle {}: {} created, {} updated, {} resolved, {} alerts{}", result.status(),
                    result.eventsCreated(), result.eventsUpdated(), result.eventsResolved(), result.alertsCreated(),
                    result.errorSummary() == null ? "" : " (" + result.errorSummary() + ")");
            return result;
        } finally {
            running.set(false);
        }
    }

    /** The last cycle that did something (not running, not skipped), for "monitoring last checked at". */
    public Optional<LastRun> lastRun() {
        return repository.lastFinishedRun();
    }

    private MonitoringResult execute() {
        try {
            FeedSnapshot snapshot = feed.read();
            if (snapshot.observed().isEmpty()) {
                return MonitoringResult.failed(String.join("; ", snapshot.failures()));
            }
            return transactions.execute(status -> apply(snapshot));
        } catch (RuntimeException e) {
            log.error("Monitoring cycle failed", e);
            return MonitoringResult.failed(e.getClass().getSimpleName());
        }
    }

    private MonitoringResult apply(FeedSnapshot snapshot) {
        if (!repository.tryLock()) {
            return MonitoringResult.skipped("Another instance is running a cycle");
        }
        Instant now = clock.instant();
        List<ActiveEvent> active = repository.activeEvents();
        ChangeDetector.Changes changes = ChangeDetector.diff(active, snapshot);

        List<ActiveEvent> current = new ArrayList<>();
        changes.created().forEach(o -> current.add(repository.insertEvent(o, now)));
        changes.updated().forEach(u -> current.add(repository.updateEvent(u.event(), u.observed(), now)));
        for (ChangeDetector.Update u : changes.unchanged()) {
            ActiveEvent e = u.event();
            if (changes.scopeChanged(u)) {
                repository.replaceScope(e.id(), u.observed());
                e = new ActiveEvent(e.id(), e.source(), e.identityKey(), e.contentHash(), e.revision(), e.title(),
                        e.description(), u.observed().lineIds(), u.observed().stationIds());
            }
            current.add(e);
        }
        repository.touch(changes.unchanged().stream().map(u -> u.event().id()).toList(), now);
        // Events from feeds that couldn't be read stay active and keep matching.
        active.stream().filter(e -> !snapshot.wasRead(e.source())).forEach(current::add);
        repository.resolve(changes.resolved().stream().map(ActiveEvent::id).toList(), now);

        int alerts = createAlerts(current, changes.resolved(), now);

        repository.deleteResolvedEventsBefore(now.minus(properties.resolvedRetention()));
        repository.deleteRunsBefore(now.minus(properties.runRetention()));

        RunStatus status = snapshot.failures().isEmpty() ? RunStatus.SUCCEEDED : RunStatus.PARTIAL;
        String error = snapshot.failures().isEmpty() ? null : String.join("; ", snapshot.failures());
        return new MonitoringResult(status, changes.created().size(), changes.updated().size(), changes.resolved().size(),
                alerts, error);
    }

    private int createAlerts(List<ActiveEvent> current, List<ActiveEvent> resolved, Instant now) {
        List<MonitoredJourney> journeys = repository.monitoredJourneys();
        if (journeys.isEmpty()) {
            return 0;
        }
        Set<AlertKey> alertedAtRevision = repository.alerted(true);
        Set<AlertKey> alertedEver = repository.alerted(false);
        int created = 0;

        for (JourneyMatcher.Match match : JourneyMatcher.match(journeys, current)) {
            AlertKey key = new AlertKey(match.journey().id(), match.event().id());
            if (alertedAtRevision.contains(key)) {
                continue;
            }
            AlertType type = alertedEver.contains(key) ? AlertType.UPDATED : AlertType.NEW;
            created += alert(match.journey(), match.event(), type, now);
        }

        Map<UUID, MonitoredJourney> byId = journeys.stream().collect(Collectors.toMap(MonitoredJourney::id, Function.identity()));
        for (ActiveEvent event : resolved) {
            for (UUID journeyId : repository.journeysAlertedFor(event.id())) {
                MonitoredJourney journey = byId.get(journeyId);
                if (journey != null) {
                    created += alert(journey, event, AlertType.RESOLVED, now);
                }
            }
        }
        return created;
    }

    private int alert(MonitoredJourney journey, ActiveEvent event, AlertType type, Instant now) {
        Optional<UUID> id = repository.insertAlert(journey.userId(), journey.id(), event, type,
                AlertMessages.title(type, event), AlertMessages.message(type, event), now);
        id.ifPresent(alertId -> repository.queueEmail(alertId, now));
        return id.isPresent() ? 1 : 0;
    }
}
