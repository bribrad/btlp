package com.topnotchbroker.btlp.job;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * Request body for updating a job. Only mutable fields are exposed; {@code id}, {@code loadId}, and
 * {@code status} are intentionally omitted so they cannot be changed through this path.
 */
public record JobUpdateRequest(
    @NotNull JobType jobType, @NotNull @Positive Integer sequence, OffsetDateTime scheduledAt) {}
