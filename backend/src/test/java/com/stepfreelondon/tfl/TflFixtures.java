package com.stepfreelondon.tfl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;

/** Loads real TfL responses captured on 2026-10-23 from {@code src/test/resources/tfl}. */
public final class TflFixtures {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    private TflFixtures() {
    }

    public static <T> T read(String name, Class<T> type) {
        try (InputStream in = new ClassPathResource("tfl/" + name).getInputStream()) {
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T read(String name, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource("tfl/" + name).getInputStream()) {
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
