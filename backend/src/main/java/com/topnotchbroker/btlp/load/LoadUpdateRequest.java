package com.topnotchbroker.btlp.load;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Request body for updating a load. Only mutable fields are exposed; {@code id}, {@code
 * customerId}, {@code status}, and audit fields are intentionally omitted so they cannot be
 * changed through this path.
 */
public record LoadUpdateRequest(
    @NotBlank @Size(max = 500) String origin,
    @NotBlank @Size(max = 500) String destination,
    OffsetDateTime pickupWindowStart,
    OffsetDateTime pickupWindowEnd,
    OffsetDateTime dropoffWindowStart,
    OffsetDateTime dropoffWindowEnd,
    @PositiveOrZero BigDecimal rateAmount,
    @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase currency code")
        String rateCurrency,
    @Size(max = 2000) String notes) {}
