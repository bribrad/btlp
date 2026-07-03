package com.topnotchbroker.btlp.job;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable domain representation of a row in the {@code jobs} table. */
public record Job(
    UUID id,
    UUID loadId,
    JobType jobType,
    int sequence,
    JobStatus status,
    OffsetDateTime scheduledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
