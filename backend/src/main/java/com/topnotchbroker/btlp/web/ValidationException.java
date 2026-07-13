package com.topnotchbroker.btlp.web;

/**
 * Thrown for domain request-validation failures detected in service logic; mapped to HTTP 400
 * {@code VALIDATION_ERROR} by {@link ApiExceptionHandler}.
 */
public class ValidationException extends RuntimeException {
  public ValidationException(String message) {
    super(message);
  }
}
