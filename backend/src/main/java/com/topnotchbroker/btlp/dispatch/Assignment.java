package com.topnotchbroker.btlp.dispatch;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable domain representation of a row in the {@code assignments} table. */
public record Assignment(
    UUID id,
    UUID jobId,
    UUID driverId,
    AssignmentState state,
    OffsetDateTime assignedAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
