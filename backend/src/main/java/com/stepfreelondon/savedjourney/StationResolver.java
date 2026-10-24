package com.stepfreelondon.savedjourney;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.stepfreelondon.accessibility.StationProfileService;
import com.stepfreelondon.accessibility.dto.StationDetailDto;
import com.stepfreelondon.common.error.RequestValidationException;
import com.stepfreelondon.tfl.TflNotFoundException;

/**
 * Finds or creates the {@link Station} row for a TfL id. New stations are looked up through TfL
 * (cached), which both confirms the id is real and supplies the ICS code needed to re-plan.
 *
 * <p>Not transactional on purpose: the TfL call happens outside any database transaction, and each
 * insert commits on its own (Spring Data's {@code saveAndFlush} is transactional).
 */
@Component
class StationResolver {

    private final StationRepository stations;
    private final StationProfileService stationProfileService;
    private final Clock clock;

    StationResolver(StationRepository stations, StationProfileService stationProfileService, Clock clock) {
        this.stations = stations;
        this.stationProfileService = stationProfileService;
        this.clock = clock;
    }

    /**
     * @param field request field name, for the validation error if TfL doesn't know the id
     */
    Station resolve(String tflStationId, String field) {
        return stations.findByTflStationId(tflStationId).orElseGet(() -> create(tflStationId, field));
    }

    private Station create(String tflStationId, String field) {
        StationDetailDto detail;
        try {
            detail = stationProfileService.detail(tflStationId).data();
        } catch (TflNotFoundException e) {
            throw new RequestValidationException(field, "is not a known TfL station");
        }
        try {
            return stations.saveAndFlush(new Station(tflStationId, detail.icsCode(), detail.name(), detail.latitude(),
                    detail.longitude(), detail.modes(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Another request inserted the same station between our lookup and insert. Use theirs.
            return stations.findByTflStationId(tflStationId).orElseThrow(() -> e);
        }
    }
}
