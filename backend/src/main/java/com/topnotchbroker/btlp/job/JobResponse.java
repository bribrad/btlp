package com.topnotchbroker.btlp.job;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of a job. */
public record JobResponse(
    UUID id,
    UUID loadId,
    JobType jobType,
    int sequence,
    JobStatus status,
    OffsetDateTime scheduledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  static JobResponse from(Job job) {
    return new JobResponse(
        job.id(),
        job.loadId(),
        job.jobType(),
        job.sequence(),
        job.status(),
        job.scheduledAt(),
        job.createdAt(),
        job.updatedAt());
  }
}
