package com.stepfreelondon.station;

import java.util.regex.Pattern;

/** Display-name cleanup for TfL station names, shared by search, journeys and accessibility. */
public final class StationNames {

    /**
     * TfL appends the station type to names ("Wembley Park Underground Station"). Mode badges already
     * convey that, so it's stripped. Parenthesised qualifiers such as "Hammersmith (H&amp;C Line)"
     * are kept because they tell otherwise identical names apart.
     */
    private static final Pattern TYPE_SUFFIX = Pattern.compile(
            "\\s+(Underground Station|Rail Station|DLR Station|Tram Stop|Station)$");

    private StationNames() {
    }

    public static String displayName(String tflName) {
        if (tflName == null) {
            return null;
        }
        String trimmed = tflName.trim();
        String stripped = TYPE_SUFFIX.matcher(trimmed).replaceFirst("");
        return stripped.isBlank() ? trimmed : stripped;
    }
}
