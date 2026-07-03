package com.topnotchbroker.btlp.load;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.OffsetDateTime;

/**
 * Validates that a load's pickup/dropoff windows are chronologically ordered. Each rule is only
 * applied when both timestamps it compares are present, so partial windows are allowed. Failures
 * are attached to the later field so the resulting message is field-level.
 */
public class ChronologicalWindowsValidator
    implements ConstraintValidator<ChronologicalWindows, LoadWindows> {

  @Override
  public boolean isValid(LoadWindows value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    boolean valid = true;

    if (isAfter(value.pickupWindowStart(), value.pickupWindowEnd())) {
      addViolation(context, "pickupWindowEnd", "must be at or after pickupWindowStart");
      valid = false;
    }
    if (isAfter(value.dropoffWindowStart(), value.dropoffWindowEnd())) {
      addViolation(context, "dropoffWindowEnd", "must be at or after dropoffWindowStart");
      valid = false;
    }
    if (isAfter(value.pickupWindowStart(), value.dropoffWindowStart())) {
      addViolation(context, "dropoffWindowStart", "must be at or after pickupWindowStart");
      valid = false;
    }
    return valid;
  }

  private static boolean isAfter(OffsetDateTime earlier, OffsetDateTime later) {
    return earlier != null && later != null && earlier.isAfter(later);
  }

  private static void addViolation(
      ConstraintValidatorContext context, String field, String message) {
    context
        .buildConstraintViolationWithTemplate(message)
        .addPropertyNode(field)
        .addConstraintViolation();
  }
}
