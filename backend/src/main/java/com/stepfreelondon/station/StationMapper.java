package com.stepfreelondon.station;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.stepfreelondon.station.dto.StationSummaryDto;
import com.stepfreelondon.tfl.model.TflStopPointSearchResponse;

@Component
class StationMapper {

    List<StationSummaryDto> toSummaries(TflStopPointSearchResponse response) {
        if (response.matches() == null) {
            return List.of();
        }
        return response.matches().stream()
                .filter(match -> match.id() != null && match.name() != null)
                .map(this::toSummary)
                .toList();
    }

    StationSummaryDto toSummary(TflStopPointSearchResponse.Match match) {
        List<String> modes = match.modes() == null ? List.of()
                : match.modes().stream().filter(Objects::nonNull).filter(StationSearchModes.RAIL::contains).sorted().toList();
        return new StationSummaryDto(
                match.id(),
                match.icsId(),
                StationNames.displayName(match.name()),
                modes,
                match.zone(),
                match.lat(),
                match.lon(),
                match.id().startsWith("HUB"));
    }
}
