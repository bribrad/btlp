package com.topnotchbroker.btlp.load;

/** Load lifecycle status. Values must match the {@code loads_status_check} DB constraint. */
public enum LoadStatus {
  PLANNED,
  ASSIGNED,
  IN_TRANSIT,
  DELIVERED,
  COMPLETED,
  CANCELED
}
