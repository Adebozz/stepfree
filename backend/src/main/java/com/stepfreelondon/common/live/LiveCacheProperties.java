package com.stepfreelondon.common.live;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cache TTLs, bound from {@code stepfree.cache.*}. See {@code docs/feasibility.md} §4 for why each
 * value was chosen.
 */
@Validated
@ConfigurationProperties("stepfree.cache")
public record LiveCacheProperties(
        @NotNull Duration stationSearchTtl,
        @NotNull Duration lineStatusTtl,
        @NotNull Duration journeyTtl,
        @NotNull Duration stationInfoTtl,
        @NotNull Duration disruptionTtl,
        @NotNull Duration liftStatusTtl,
        @NotNull Duration staleMaxAge) {
}
