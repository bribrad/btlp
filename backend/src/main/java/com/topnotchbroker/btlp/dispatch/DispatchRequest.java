package com.topnotchbroker.btlp.dispatch;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for dispatching a driver to a job. The resulting assignment is created with state
 * {@code PENDING}; the driver must accept before the job transitions to {@code ASSIGNED}.
 */
public record DispatchRequest(
    @NotNull UUID jobId,
    @NotNull UUID driverId) {}
