package com.topnotchbroker.btlp.web;

/** Thrown when a requested resource does not exist; mapped to HTTP 404 by {@link ApiExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
