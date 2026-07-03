package com.topnotchbroker.btlp.load;

import java.time.OffsetDateTime;

/**
 * Exposes the load pickup/dropoff time-window fields for cross-field timeline validation. Both
 * {@link LoadCreateRequest} and {@link LoadUpdateRequest} implement this via their record accessors.
 */
public interface LoadWindows {
  OffsetDateTime pickupWindowStart();

  OffsetDateTime pickupWindowEnd();

  OffsetDateTime dropoffWindowStart();

  OffsetDateTime dropoffWindowEnd();
}
