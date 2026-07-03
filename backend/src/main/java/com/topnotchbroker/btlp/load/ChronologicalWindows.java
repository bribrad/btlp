package com.topnotchbroker.btlp.load;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint enforcing chronological ordering of a load's pickup/dropoff windows.
 * Violations are reported against the specific offending field so callers get field-level messages.
 */
@Documented
@Constraint(validatedBy = ChronologicalWindowsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChronologicalWindows {
  String message() default "invalid time-window ordering";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
