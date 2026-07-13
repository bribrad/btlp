package com.topnotchbroker.btlp.driver;

import jakarta.validation.constraints.NotNull;

/** Request body for activating/deactivating a driver. */
public record StatusUpdateRequest(@NotNull DriverStatus status) {}
