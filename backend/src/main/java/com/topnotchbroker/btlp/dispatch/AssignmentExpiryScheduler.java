package com.topnotchbroker.btlp.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically expires stale {@code PENDING} assignments. Intentionally thin: the cadence lives in
 * one place and the actual work is in {@link AssignmentService#expireStaleAssignments()}, which
 * tests can invoke directly for deterministic behavior.
 */
@Component
public class AssignmentExpiryScheduler {

  private static final Logger log = LoggerFactory.getLogger(AssignmentExpiryScheduler.class);

  private final AssignmentService assignmentService;

  public AssignmentExpiryScheduler(AssignmentService assignmentService) {
    this.assignmentService = assignmentService;
  }

  @Scheduled(fixedDelayString = "${btlp.dispatch.expiry-sweep-interval:PT1M}")
  public void sweep() {
    int expired = assignmentService.expireStaleAssignments();
    if (expired > 0) {
      log.info("Expired {} stale pending assignment(s)", expired);
    }
  }
}
