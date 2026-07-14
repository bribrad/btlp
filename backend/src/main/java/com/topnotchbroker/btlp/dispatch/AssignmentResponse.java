package com.topnotchbroker.btlp.dispatch;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of an assignment. */
public record AssignmentResponse(
    UUID id,
    UUID jobId,
    UUID driverId,
    AssignmentState state,
    OffsetDateTime assignedAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  static AssignmentResponse from(Assignment assignment) {
    return new AssignmentResponse(
        assignment.id(),
        assignment.jobId(),
        assignment.driverId(),
        assignment.state(),
        assignment.assignedAt(),
        assignment.acceptedAt(),
        assignment.createdAt(),
        assignment.updatedAt());
  }
}
