package com.topnotchbroker.btlp.driver;

/** Driver availability. Values must match the {@code drivers_availability_check} DB constraint. */
public enum DriverAvailability {
  AVAILABLE,
  UNAVAILABLE,
  ON_TRIP
}
