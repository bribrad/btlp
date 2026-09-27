package com.topnotchbroker.btlp.activity;

import com.topnotchbroker.btlp.audit.AuditAction;
import com.topnotchbroker.btlp.audit.AuditEntityType;
import com.topnotchbroker.btlp.job.JobType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry on the operations timeline: an audit event resolved against the load, job, assignment,
 * and driver it concerns.
 *
 * <p>Context fields are nullable and narrow with the entity type — a load event carries no job, and
 * only an assignment event carries a driver. They are also null when the referenced row has since
 * been removed; the event itself still belongs on the timeline.
 *
 * @param sequence monotonic ordering key, descending on the timeline
 * @param detail the audited entity's resulting status/state, or null where the action has none
 */
public record ActivityEvent(
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
    String driverName) {}
