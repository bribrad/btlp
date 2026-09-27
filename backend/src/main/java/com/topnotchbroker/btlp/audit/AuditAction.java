package com.topnotchbroker.btlp.audit;

/**
 * Lifecycle actions captured by the audit trail. Must match the {@code audit_events} CHECK.
 *
 * <p>{@link #CREATE} and {@link #UPDATE} cover plain CRUD on loads and jobs. The remaining verbs
 * name a specific dispatch transition, so the operations timeline can say what happened rather than
 * only that something changed.
 */
public enum AuditAction {
  CREATE,
  UPDATE,
  /** A job's status moved as a side effect of a dispatch transition. */
  STATUS_CHANGE,
  ASSIGN,
  REASSIGN,
  CANCEL,
  ACCEPT,
  REJECT,
  EXPIRE,
  COMPLETE
}
