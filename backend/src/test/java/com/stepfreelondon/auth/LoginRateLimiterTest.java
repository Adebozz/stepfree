package com.stepfreelondon.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(new AuthProperties(3, 5, Duration.ofMinutes(15)), clock);

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    @Test
    void blocksAnEmailAfterTheLimitWithTheTimeUntilTheWindowEnds() {
        fail("a@example.com", "10.0.0.1", 3);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "10.0.0.2"))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        e -> assertThat(e.retryAfter()).contains(Duration.ofMinutes(10)));
        assertThatCode(() -> limiter.checkAllowed("b@example.com", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        fail("a@example.com", "10.0.0.1", 3);
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "10.0.0.1")).doesNotThrowAnyException();
        // A new window starts from the next failure, not from the old count.
        fail("a@example.com", "10.0.0.1", 2);
        assertThatCode(() -> limiter.checkAllowed("a@example.com", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnIpSprayingManyAccounts() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("victim" + i + "@example.com", "10.0.0.9");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("someone-new@example.com", "10.0.0.9"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("someone-new@example.com", "10.0.0.10")).doesNotThrowAnyException();
    }

    @Test
    void successClearsTheEmailCountButNotTheIpCount() {
        fail("a@example.com", "10.0.0.1", 2);
        limiter.recordSuccess("a@example.com");
        fail("a@example.com", "10.0.0.1", 2);

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "10.0.0.3")).doesNotThrowAnyException();
        fail("c@example.com", "10.0.0.1", 1);
        assertThatThrownBy(() -> limiter.checkAllowed("d@example.com", "10.0.0.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }
}
