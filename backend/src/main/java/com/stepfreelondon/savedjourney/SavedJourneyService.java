package com.stepfreelondon.savedjourney;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.stepfreelondon.journey.AccessibilityPreference;
import com.stepfreelondon.journey.JourneyPreference;
import com.stepfreelondon.savedjourney.dto.CreateSavedJourneyRequest;
import com.stepfreelondon.savedjourney.dto.RouteStopDto;
import com.stepfreelondon.savedjourney.dto.SavedJourneyDto;
import com.stepfreelondon.savedjourney.dto.SavedJourneyDto.StationRefDto;
import com.stepfreelondon.savedjourney.dto.UpdateSavedJourneyRequest;

/** Every method takes the signed-in user's id and only ever touches that user's journeys. */
@Service
public class SavedJourneyService {

    private final SavedJourneyRepository journeys;
    private final StationResolver stationResolver;
    private final SavedJourneyProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    SavedJourneyService(SavedJourneyRepository journeys, StationResolver stationResolver, SavedJourneyProperties properties,
                        TransactionTemplate transaction, Clock clock) {
        this.journeys = journeys;
        this.stationResolver = stationResolver;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SavedJourneyDto> list(UUID userId) {
        return journeys.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(SavedJourneyService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SavedJourneyDto get(UUID userId, UUID journeyId) {
        return toDto(load(userId, journeyId));
    }

    /**
     * Cheap checks first, then station resolution (may call TfL, outside any transaction), then the
     * insert in a short transaction. The UNIQUE(user_id, name) constraint backs up the name check if
     * two requests race. The count limit can be exceeded by a concurrent request or two; it is a
     * fairness cap, not a security boundary.
     */
    public SavedJourneyDto create(UUID userId, CreateSavedJourneyRequest request) {
        String name = request.name().strip();
        if (journeys.countByUserId(userId) >= properties.maxPerUser()) {
            throw new SavedJourneyLimitException(properties.maxPerUser());
        }
        if (journeys.existsByUserIdAndName(userId, name)) {
            throw new DuplicateJourneyNameException();
        }
        Station origin = stationResolver.resolve(request.originId(), "originId");
        Station destination = stationResolver.resolve(request.destinationId(), "destinationId");

        SavedJourney journey = new SavedJourney(userId, name, origin, destination,
                request.accessibility().stream().map(Enum::name).toList(),
                request.preference() == null ? null : request.preference().name(),
                request.route().lineIds().stream().distinct().toList(),
                distinctStops(request.route().stations()),
                clock.instant());
        try {
            return transaction.execute(status -> toDto(journeys.saveAndFlush(journey)));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateJourneyNameException();
        }
    }

    @Transactional
    public SavedJourneyDto update(UUID userId, UUID journeyId, UpdateSavedJourneyRequest request) {
        SavedJourney journey = load(userId, journeyId);
        if (request.name() != null) {
            String name = request.name().strip();
            if (!name.equals(journey.getName()) && journeys.existsByUserIdAndName(userId, name)) {
                throw new DuplicateJourneyNameException();
            }
            journey.rename(name, clock.instant());
        }
        if (request.monitoringEnabled() != null) {
            journey.setMonitoringEnabled(request.monitoringEnabled(), clock.instant());
        }
        try {
            return toDto(journeys.saveAndFlush(journey));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateJourneyNameException();
        }
    }

    @Transactional
    public void delete(UUID userId, UUID journeyId) {
        journeys.delete(load(userId, journeyId));
    }

    private SavedJourney load(UUID userId, UUID journeyId) {
        return journeys.findByIdAndUserId(journeyId, userId).orElseThrow(SavedJourneyNotFoundException::new);
    }

    /** A stop can appear twice (e.g. as a leg's end and the next leg's start); the table keys on stop id, so keep the first. */
    static List<RouteStation> distinctStops(List<RouteStopDto> stops) {
        Set<String> seen = new HashSet<>();
        return stops.stream()
                .filter(stop -> seen.add(stop.id()))
                .map(stop -> new RouteStation(stop.id(), stop.role()))
                .toList();
    }

    private static SavedJourneyDto toDto(SavedJourney journey) {
        return new SavedJourneyDto(
                journey.getId(),
                journey.getName(),
                stationRef(journey.getOrigin()),
                stationRef(journey.getDestination()),
                journey.getAccessibilityPreferences().stream().map(AccessibilityPreference::valueOf).toList(),
                journey.getJourneyPreference() == null ? null : JourneyPreference.valueOf(journey.getJourneyPreference()),
                journey.isMonitoringEnabled(),
                List.copyOf(journey.getLineIds()),
                journey.getStations().stream().map(s -> new RouteStopDto(s.getTflStationId(), s.getRole())).toList(),
                journey.getCreatedAt(),
                journey.getUpdatedAt());
    }

    private static StationRefDto stationRef(Station station) {
        return new StationRefDto(station.getTflStationId(), station.getIcsCode(), station.getName());
    }
}
