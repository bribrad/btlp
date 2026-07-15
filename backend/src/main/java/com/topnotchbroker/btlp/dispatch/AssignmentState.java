package com.topnotchbroker.btlp.dispatch;

/** Assignment lifecycle state. Values must match the {@code assignments_state_check} DB constraint. */
public enum AssignmentState {
  PENDING,
  ACCEPTED,
  REJECTED,
  EXPIRED,
  CANCELED,
  COMPLETED;

  /**
   * Whether a direct transition from this state to {@code target} is legal. Terminal states
   * ({@code REJECTED}, {@code EXPIRED}, {@code CANCELED}, {@code COMPLETED}) allow no transitions.
   */
  public boolean canTransitionTo(AssignmentState target) {
    return switch (this) {
      case PENDING -> target == ACCEPTED || target == REJECTED || target == EXPIRED;
      case ACCEPTED -> target == COMPLETED;
      default -> false;
    };
  }
}
