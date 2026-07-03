package com.topnotchbroker.btlp.load;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable domain representation of a row in the {@code loads} table. */
public record Load(
    UUID id,
    String customerId,
    String origin,
    String destination,
    OffsetDateTime pickupWindowStart,
    OffsetDateTime pickupWindowEnd,
    OffsetDateTime dropoffWindowStart,
    OffsetDateTime dropoffWindowEnd,
    BigDecimal rateAmount,
    String rateCurrency,
    String notes,
    LoadStatus status,
    String createdBy,
    String updatedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
