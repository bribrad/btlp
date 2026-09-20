package com.topnotchbroker.btlp.dispatch;

import com.topnotchbroker.btlp.job.JobStatus;
import com.topnotchbroker.btlp.job.JobType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the dispatch board: an actionable job joined to its load, its active assignment, and
 * the assigned driver. This is a read-only projection across four tables rather than a single
 * table's domain record, so it is both the repository result and the response body — there is no
 * separate DTO to map to.
 *
 * <p>The assignment and driver fields are null for a job in the {@code NEEDS_DISPATCH} lane.
 */
public record DispatchBoardEntry(
    UUID jobId,
    UUID loadId,
    JobType jobType,
    int sequence,
    JobStatus jobStatus,
    OffsetDateTime scheduledAt,
    String origin,
    String destination,
    DispatchBoardLane lane,
    UUID assignmentId,
    AssignmentState assignmentState,
    OffsetDateTime assignedAt,
    OffsetDateTime expiresAt,
    String assignedBy,
    UUID driverId,
    String driverName,
    String driverPhone) {}
