package com.topnotchbroker.btlp.driver;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of a driver. */
public record DriverResponse(
    UUID id,
    String name,
    String phone,
    String licenseNumber,
    DriverAvailability availability,
    DriverStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  static DriverResponse from(Driver driver) {
    return new DriverResponse(
        driver.id(),
        driver.name(),
        driver.phone(),
        driver.licenseNumber(),
        driver.availability(),
        driver.status(),
        driver.createdAt(),
        driver.updatedAt());
  }
}
