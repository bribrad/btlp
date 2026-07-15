package com.topnotchbroker.btlp.dispatch;

/** Assignment lifecycle state. Values must match the {@code assignments_state_check} DB constraint. */
public enum AssignmentState {
  PENDING,
  ACCEPTED,
  REJECTED,
  EXPIRED,
  CANCELED
}
