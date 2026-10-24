package com.stepfreelondon.accessibility;

import java.util.List;

import com.stepfreelondon.accessibility.dto.StationAccessibilityDto.SectionDto;
import com.stepfreelondon.accessibility.dto.StationDetailDto;

/**
 * The cacheable, slow-changing part of a station: identity plus TfL's static accessibility and
 * facility properties. {@code memberIds} are the NaPTAN ids used to look up live disruptions.
 */
record StationProfile(StationDetailDto detail, List<SectionDto> sections, List<String> memberIds) {
}
