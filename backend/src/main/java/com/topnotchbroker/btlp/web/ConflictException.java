package com.topnotchbroker.btlp.web;

/**
 * Thrown for domain-level conflict conditions detected in service logic; mapped to HTTP 409
 * {@code CONFLICT} by {@link ApiExceptionHandler}.
 */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
