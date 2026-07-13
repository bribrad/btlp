package com.topnotchbroker.btlp.driver;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for toggling a driver's availability. {@code ON_TRIP} is reserved for the assignment
 * lifecycle and is rejected by the service.
 */
public record AvailabilityUpdateRequest(@NotNull DriverAvailability availability) {}
