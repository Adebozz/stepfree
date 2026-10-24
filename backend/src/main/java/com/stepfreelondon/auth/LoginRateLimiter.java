package com.stepfreelondon.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;

/**
 * Counts failed logins per email and per client IP in fixed windows, and refuses further attempts
 * once a limit is reached. Only failures count, so a user who signs in correctly is never blocked.
 *
 * <p>The per-email limit stops password guessing against one account; the per-IP limit stops one
 * client spraying many accounts. Trade-off: anyone can lock a known email out for one window. That is
 * the usual price of per-account limits and is preferable to unlimited guessing.
 *
 * <p>State is in memory, so limits are per instance and reset on restart. That fits the single-instance
 * deployment; several instances would need a shared store (e.g. a database table or Redis).
 */
@Component
class LoginRateLimiter {

    private record Window(int failures, Instant resetsAt) {
    }

    private final AuthProperties properties;
    private final Clock clock;
    private final Cache<String, Window> windows;

    LoginRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // Bounded so a flood of random emails can't exhaust memory; expired windows are evicted anyway.
        this.windows = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(properties.failureWindow()).build();
    }

    /** @throws TooManyLoginAttemptsException if either the email or the IP has used up its failures */
    void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        Stream.of(blockedUntil(emailKey(email), properties.maxFailuresPerEmail(), now),
                        blockedUntil(ipKey(ip), properties.maxFailuresPerIp(), now))
                .flatMap(Optional::stream)
                .max(Instant::compareTo)
                .ifPresent(until -> {
                    throw new TooManyLoginAttemptsException(Duration.between(now, until));
                });
    }

    void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        for (String key : new String[] {emailKey(email), ipKey(ip)}) {
            windows.asMap().compute(key, (k, window) -> window == null || !window.resetsAt().isAfter(now)
                    ? new Window(1, now.plus(properties.failureWindow()))
                    : new Window(window.failures() + 1, window.resetsAt()));
        }
    }

    /** A correct password clears the account's count (not the IP's, which may be guessing other accounts). */
    void recordSuccess(String email) {
        windows.invalidate(emailKey(email));
    }

    private Optional<Instant> blockedUntil(String key, int limit, Instant now) {
        Window window = windows.getIfPresent(key);
        if (window == null || !window.resetsAt().isAfter(now) || window.failures() < limit) {
            return Optional.empty();
        }
        return Optional.of(window.resetsAt());
    }

    private static String emailKey(String email) {
        return "email:" + email;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }
}
