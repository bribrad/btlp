package com.topnotchbroker.btlp.driver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a driver. {@code availability} defaults to {@code AVAILABLE}; {@code
 * status} defaults to {@code ACTIVE} when omitted.
 */
public record DriverCreateRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 40) String phone,
    @Size(max = 100) String licenseNumber,
    DriverStatus status) {}
