package com.stepfreelondon.station;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;

import com.stepfreelondon.station.dto.StationSummaryDto;
import com.stepfreelondon.tfl.model.TflStopPointSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;

class StationMapperTest {

    private final StationMapper mapper = new StationMapper();

    @Test
    void mapsRealWembleySearchIncludingHubsAndIcsCodes() throws IOException {
        TflStopPointSearchResponse response = JsonMapper.builder().build().readValue(
                new ClassPathResource("tfl/stop-point-search-wembley.json").getInputStream(), TflStopPointSearchResponse.class);

        List<StationSummaryDto> stations = mapper.toSummaries(response);

        assertThat(stations).extracting(StationSummaryDto::name)
                .containsExactly("Wembley Central", "North Wembley", "Wembley Park", "Wembley Stadium");

        StationSummaryDto central = stations.getFirst();
        assertThat(central.id()).isEqualTo("HUBWMB");
        assertThat(central.icsCode()).isEqualTo("1000256");
        assertThat(central.hub()).isTrue();
        assertThat(central.modes()).as("bus is dropped; rail modes sorted").containsExactly("national-rail", "overground", "tube");

        StationSummaryDto park = stations.get(2);
        assertThat(park.id()).isEqualTo("940GZZLUWYP");
        assertThat(park.hub()).isFalse();
        assertThat(park.latitude()).isNotNull();
    }

    @Test
    void stripsStationTypeSuffixButKeepsDistinguishingQualifiers() {
        assertThat(StationNames.displayName("Wembley Park Underground Station")).isEqualTo("Wembley Park");
        assertThat(StationNames.displayName("Wembley Stadium Rail Station")).isEqualTo("Wembley Stadium");
        assertThat(StationNames.displayName("Canary Wharf DLR Station")).isEqualTo("Canary Wharf");
        assertThat(StationNames.displayName("Wimbledon Tram Stop")).isEqualTo("Wimbledon");
        assertThat(StationNames.displayName("Hammersmith (H&C Line) Underground Station")).isEqualTo("Hammersmith (H&C Line)");
        assertThat(StationNames.displayName("Bank")).isEqualTo("Bank");
    }

    @Test
    void handlesMissingFieldsWithoutFailing() {
        var noModes = new TflStopPointSearchResponse.Match("940GZZLUXXX", null, "Somewhere Underground Station", null, null, null, null);
        var noId = new TflStopPointSearchResponse.Match(null, "1", "Nowhere", List.of("tube"), null, null, null);

        List<StationSummaryDto> result = mapper.toSummaries(new TflStopPointSearchResponse("x", 2, Arrays.asList(noModes, noId)));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Somewhere");
            assertThat(s.modes()).isEmpty();
            assertThat(s.icsCode()).isNull();
        });
    }

    @Test
    void emptyOrNullMatchesGiveEmptyList() {
        assertThat(mapper.toSummaries(new TflStopPointSearchResponse("zzz", 0, List.of()))).isEmpty();
        assertThat(mapper.toSummaries(new TflStopPointSearchResponse("zzz", 0, null))).isEmpty();
    }

    @Test
    void queryNormalisationSharesCacheEntries() {
        assertThat(StationService.normalise("  Wembley   PARK ")).isEqualTo("wembley park");
    }
}
