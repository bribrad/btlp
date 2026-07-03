package com.topnotchbroker.btlp.load;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Request body for creating a load. Audit fields and status are set server-side. */
public record LoadCreateRequest(
    @Size(max = 100) String customerId,
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
