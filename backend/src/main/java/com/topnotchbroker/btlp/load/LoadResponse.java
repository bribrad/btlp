package com.topnotchbroker.btlp.load;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of a load, including server-managed status and audit fields. */
public record LoadResponse(
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
    OffsetDateTime updatedAt) {

  static LoadResponse from(Load load) {
    return new LoadResponse(
        load.id(),
        load.customerId(),
        load.origin(),
        load.destination(),
        load.pickupWindowStart(),
        load.pickupWindowEnd(),
        load.dropoffWindowStart(),
        load.dropoffWindowEnd(),
        load.rateAmount(),
        load.rateCurrency(),
        load.notes(),
        load.status(),
        load.createdBy(),
        load.updatedBy(),
        load.createdAt(),
        load.updatedAt());
  }
}
