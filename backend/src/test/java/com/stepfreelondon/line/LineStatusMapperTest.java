package com.stepfreelondon.line;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;

import com.stepfreelondon.line.dto.LineStatusDto;
import com.stepfreelondon.tfl.model.TflLine;
import com.stepfreelondon.tfl.model.TflLine.LineStatus;
import com.stepfreelondon.tfl.model.TflLine.ValidityPeriod;

import static org.assertj.core.api.Assertions.assertThat;

class LineStatusMapperTest {

    private final LineStatusMapper mapper = new LineStatusMapper();

    @Test
    void mapsRealTflSnapshot() throws IOException {
        List<TflLine> lines = JsonMapper.builder().build().readValue(
                new ClassPathResource("tfl/line-status-2026-10-23.json").getInputStream(), new TypeReference<>() { });

        List<LineStatusDto> dtos = mapper.toDtos(lines);

        assertThat(dtos).hasSize(lines.size());
        assertThat(dtos.getFirst().mode()).as("tube lines first").isEqualTo("tube");

        LineStatusDto central = find(dtos, "central");
        assertThat(central.level()).isEqualTo(ServiceLevel.MINOR_DISRUPTION);
        assertThat(central.summary()).isEqualTo("Minor Delays");
        assertThat(central.statuses().getFirst().reason()).startsWith("Central Line: Minor delays");
        assertThat(central.statuses().getFirst().validFrom()).isEqualTo(Instant.parse("2026-10-23T14:44:33Z"));

        assertThat(find(dtos, "suffragette").level()).isEqualTo(ServiceLevel.CLOSED);
        assertThat(find(dtos, "bakerloo").level()).isEqualTo(ServiceLevel.GOOD_SERVICE);
    }

    @Test
    void lineWithNoStatusesIsUnknownNeverGoodService() {
        LineStatusDto dto = mapper.toDto(new TflLine("jubilee", "Jubilee", "tube", List.of()));
        assertThat(dto.level()).isEqualTo(ServiceLevel.UNKNOWN);
        assertThat(dto.summary()).isEqualTo(LineStatusMapper.STATUS_UNAVAILABLE);

        LineStatusDto nullStatuses = mapper.toDto(new TflLine("jubilee", "Jubilee", "tube", null));
        assertThat(nullStatuses.level()).isEqualTo(ServiceLevel.UNKNOWN);
    }

    @Test
    void overallLevelIsTheMostSeriousStatus() {
        TflLine line = new TflLine("district", "District", "tube", List.of(
                status(9, "Minor Delays"),
                status(5, "Part Closure"),
                status(10, "Good Service")));

        LineStatusDto dto = mapper.toDto(line);

        assertThat(dto.level()).isEqualTo(ServiceLevel.SEVERE_DISRUPTION);
        assertThat(dto.summary()).isEqualTo("Part Closure");
        assertThat(dto.statuses()).extracting(s -> s.severityCode()).containsExactly(5, 9, 10);
    }

    @Test
    void flagsNoStepFreeAccessStatus() {
        LineStatusDto dto = mapper.toDto(new TflLine("victoria", "Victoria", "tube", List.of(status(13, "No Step Free Access"))));

        assertThat(dto.statuses().getFirst().stepFreeAccessAffected()).isTrue();
        assertThat(dto.level()).isEqualTo(ServiceLevel.SEVERE_DISRUPTION);
    }

    @Test
    void prefersTheCurrentValidityPeriodAndToleratesBadDates() {
        LineStatus status = new LineStatus(9, "Minor Delays", " Delays. ", List.of(
                new ValidityPeriod("2026-10-20T08:00:00Z", "2026-10-20T09:00:00Z", false),
                new ValidityPeriod("garbage", "2026-10-23T23:00:00Z", true)));

        LineStatusDto dto = mapper.toDto(new TflLine("central", "Central", "tube", List.of(status)));

        assertThat(dto.statuses().getFirst().validFrom()).isNull();
        assertThat(dto.statuses().getFirst().validTo()).isEqualTo(Instant.parse("2026-10-23T23:00:00Z"));
        assertThat(dto.statuses().getFirst().reason()).isEqualTo("Delays.");
    }

    @ParameterizedTest(name = "TfL severity {0} -> {1}")
    @CsvSource({
            "10, GOOD_SERVICE", "18, GOOD_SERVICE",
            "0, INFORMATION", "19, INFORMATION",
            "9, MINOR_DISRUPTION", "7, MINOR_DISRUPTION", "15, MINOR_DISRUPTION",
            "6, SEVERE_DISRUPTION", "3, SEVERE_DISRUPTION", "13, SEVERE_DISRUPTION",
            "1, CLOSED", "2, CLOSED", "20, CLOSED",
            "99, UNKNOWN"})
    void mapsTflSeverityCodes(int code, ServiceLevel expected) {
        assertThat(ServiceLevel.fromTflSeverity(code)).isEqualTo(expected);
    }

    @Test
    void unknownSeverityOutranksGoodService() {
        assertThat(ServiceLevel.fromTflSeverity(null)).isEqualTo(ServiceLevel.UNKNOWN);
        assertThat(ServiceLevel.UNKNOWN).isGreaterThan(ServiceLevel.GOOD_SERVICE);
    }

    private static LineStatus status(int code, String description) {
        return new LineStatus(code, description, null, List.of());
    }

    private static LineStatusDto find(List<LineStatusDto> dtos, String id) {
        return dtos.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }
}
