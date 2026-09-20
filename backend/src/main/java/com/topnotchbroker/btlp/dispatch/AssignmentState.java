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
   *
   * <p>A dispatcher may pull an assignment back with {@code CANCELED} from either active state —
   * before the driver has answered ({@code PENDING}) or after they accepted ({@code ACCEPTED}) —
   * which is what makes reassignment possible.
   */
  public boolean canTransitionTo(AssignmentState target) {
    return switch (this) {
      case PENDING -> target == ACCEPTED || target == REJECTED || target == EXPIRED
          || target == CANCELED;
      case ACCEPTED -> target == COMPLETED || target == CANCELED;
      default -> false;
    };
  }

  /** Whether this state still occupies the job, blocking a second dispatch. */
  public boolean isActive() {
    return this == PENDING || this == ACCEPTED;
  }
}
