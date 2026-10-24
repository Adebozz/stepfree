package com.stepfreelondon.notification;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code stepfree.notifications}.
 *
 * @param fromAddress  sender address for alert emails
 * @param appBaseUrl   public URL of the app, used for links in emails
 * @param retryBackoff wait before each retry; its size + 1 is the maximum number of attempts
 * @param batchSize    deliveries claimed per dispatch
 * @param stuckAfter   a delivery left SENDING this long (crash mid-send) is retried
 */
@ConfigurationProperties("stepfree.notifications")
record NotificationProperties(String fromAddress, String appBaseUrl, List<Duration> retryBackoff, int batchSize,
                              Duration stuckAfter) {

    int maxAttempts() {
        return retryBackoff.size() + 1;
    }
}
