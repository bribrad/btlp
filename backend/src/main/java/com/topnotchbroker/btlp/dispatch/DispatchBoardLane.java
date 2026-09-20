package com.topnotchbroker.btlp.dispatch;

/**
 * The column a job occupies on the dispatch board, derived from its active assignment rather than
 * stored: a job with no {@code PENDING}/{@code ACCEPTED} assignment needs dispatching, one waiting
 * on a driver's answer is {@code PENDING_ACCEPTANCE}, and an accepted one is {@code IN_PROGRESS}.
 */
public enum DispatchBoardLane {
  NEEDS_DISPATCH,
  PENDING_ACCEPTANCE,
  IN_PROGRESS;

  static DispatchBoardLane from(AssignmentState state) {
    if (state == null) {
      return NEEDS_DISPATCH;
    }
    return state == AssignmentState.PENDING ? PENDING_ACCEPTANCE : IN_PROGRESS;
  }
}
