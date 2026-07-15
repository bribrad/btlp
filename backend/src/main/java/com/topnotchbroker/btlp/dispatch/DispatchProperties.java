package com.topnotchbroker.btlp.dispatch;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for the assignment lifecycle, bound from the {@code btlp.dispatch} configuration prefix.
 *
 * @param assignmentTimeout how long a {@code PENDING} assignment remains valid before it is eligible
 *     for expiration (the driver's window to accept).
 * @param expirySweepInterval how often the background sweeper looks for stale pending assignments.
 */
@ConfigurationProperties("btlp.dispatch")
public record DispatchProperties(
    @DefaultValue("PT15M") Duration assignmentTimeout,
    @DefaultValue("PT1M") Duration expirySweepInterval) {}
