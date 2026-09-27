package com.topnotchbroker.btlp.activity;

import com.topnotchbroker.btlp.audit.AuditAction;
import com.topnotchbroker.btlp.audit.AuditEntityType;
import com.topnotchbroker.btlp.job.JobType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of a timeline entry. */
public record ActivityEventResponse(
    UUID id,
    long sequence,
    OffsetDateTime occurredAt,
    String actor,
    AuditEntityType entityType,
    UUID entityId,
    AuditAction action,
    String detail,
    UUID loadId,
    String origin,
    String destination,
    UUID jobId,
    JobType jobType,
    Integer jobSequence,
    UUID assignmentId,
    UUID driverId,
    String driverName) {

  static ActivityEventResponse from(ActivityEvent event) {
    return new ActivityEventResponse(
        event.id(),
        event.sequence(),
        event.occurredAt(),
        event.actor(),
        event.entityType(),
        event.entityId(),
        event.action(),
        event.detail(),
        event.loadId(),
        event.origin(),
        event.destination(),
        event.jobId(),
        event.jobType(),
        event.jobSequence(),
        event.assignmentId(),
        event.driverId(),
        event.driverName());
  }
}
