package com.topnotchbroker.btlp.job;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for creating a job. {@code status} is set to {@code UNASSIGNED} server-side; when
 * {@code sequence} is omitted the server assigns the next value for the load.
 */
public record JobCreateRequest(
    @NotNull UUID loadId,
    @NotNull JobType jobType,
    @Positive Integer sequence,
    OffsetDateTime scheduledAt) {}
