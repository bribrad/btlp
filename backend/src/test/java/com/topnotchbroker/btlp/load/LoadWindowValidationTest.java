package com.topnotchbroker.btlp.load;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Standalone bean-validation tests for the {@link ChronologicalWindows} constraint on loads. */
class LoadWindowValidationTest {

  private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-01T10:00:00Z");
  private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-01-02T10:00:00Z");
  private static final OffsetDateTime T3 = OffsetDateTime.parse("2026-01-03T10:00:00Z");
  private static final OffsetDateTime T4 = OffsetDateTime.parse("2026-01-04T10:00:00Z");

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void wellOrderedWindowsHaveNoViolations() {
    assertThat(validator.validate(load(T1, T2, T3, T4))).isEmpty();
  }

  @Test
  void partialWindowsAreAllowed() {
    assertThat(validator.validate(load(T1, null, null, T4))).isEmpty();
    assertThat(validator.validate(load(null, null, null, null))).isEmpty();
  }

  @Test
  void pickupStartAfterPickupEndIsReportedOnPickupWindowEnd() {
    assertThat(violationProps(load(T2, T1, null, null))).contains("pickupWindowEnd");
  }

  @Test
  void dropoffStartAfterDropoffEndIsReportedOnDropoffWindowEnd() {
    assertThat(violationProps(load(null, null, T4, T3))).contains("dropoffWindowEnd");
  }

  @Test
  void pickupStartAfterDropoffStartIsReportedOnDropoffWindowStart() {
    assertThat(violationProps(load(T3, T4, T1, T2))).contains("dropoffWindowStart");
  }

  private static LoadCreateRequest load(
      OffsetDateTime pickupStart,
      OffsetDateTime pickupEnd,
      OffsetDateTime dropoffStart,
      OffsetDateTime dropoffEnd) {
    return new LoadCreateRequest(
        "ACME", "Origin", "Dest", pickupStart, pickupEnd, dropoffStart, dropoffEnd, null, null, null);
  }

  private static Set<String> violationProps(LoadCreateRequest request) {
    return validator.validate(request).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }
}
