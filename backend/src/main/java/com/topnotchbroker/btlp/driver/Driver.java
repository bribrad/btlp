package com.topnotchbroker.btlp.driver;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable domain representation of a row in the {@code drivers} table (directory view). */
public record Driver(
    UUID id,
    String name,
    String phone,
    String licenseNumber,
    DriverAvailability availability,
    DriverStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
