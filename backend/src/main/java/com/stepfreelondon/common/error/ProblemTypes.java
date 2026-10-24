package com.stepfreelondon.common.error;

import java.net.URI;

/**
 * Stable {@code type} identifiers for RFC 9457 problem responses. Clients may switch on these;
 * they are relative URI references, which RFC 9457 permits.
 */
public final class ProblemTypes {

    public static final URI VALIDATION = URI.create("/problems/validation-failed");
    public static final URI NOT_FOUND = URI.create("/problems/not-found");
    public static final URI UPSTREAM_UNAVAILABLE = URI.create("/problems/upstream-unavailable");
    public static final URI UPSTREAM_RATE_LIMITED = URI.create("/problems/upstream-rate-limited");
    public static final URI UNAUTHENTICATED = URI.create("/problems/unauthenticated");
    public static final URI FORBIDDEN = URI.create("/problems/forbidden");
    public static final URI INTERNAL = URI.create("/problems/internal-error");

    private ProblemTypes() {
    }
}
