package com.topnotchbroker.btlp.web;

/**
 * Thrown when a requested lifecycle transition is not legal for the entity's current state; mapped
 * to HTTP 409 with the deterministic {@code INVALID_STATE_TRANSITION} code by {@link
 * ApiExceptionHandler}. Distinct from {@link ConflictException} (a resource-level conflict) so
 * clients can reliably branch on an illegal-transition outcome.
 */
public class InvalidStateTransitionException extends RuntimeException {
  public InvalidStateTransitionException(String message) {
    super(message);
  }
}
