package com.topnotchbroker.btlp.job;

/** Job lifecycle status. Values must match the {@code jobs_status_check} DB constraint. */
public enum JobStatus {
  UNASSIGNED,
  ASSIGNED,
  EN_ROUTE,
  ARRIVED,
  IN_PROGRESS,
  COMPLETED,
  CANCELED
}
