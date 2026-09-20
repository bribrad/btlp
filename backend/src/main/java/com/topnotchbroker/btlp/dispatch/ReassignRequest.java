package com.topnotchbroker.btlp.dispatch;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for moving a job's active assignment to another driver. Cancels the current
 * assignment and dispatches a fresh {@code PENDING} one to {@code driverId}.
 */
public record ReassignRequest(@NotNull UUID driverId) {}
