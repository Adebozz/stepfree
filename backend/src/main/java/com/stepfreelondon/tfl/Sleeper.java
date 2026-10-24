package com.stepfreelondon.tfl;

import java.time.Duration;

/** Pauses between retries. Exists so tests can retry instantly. */
@FunctionalInterface
public interface Sleeper {

    Sleeper THREAD_SLEEP = duration -> Thread.sleep(duration);

    void sleep(Duration duration) throws InterruptedException;
}
