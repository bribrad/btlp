package com.topnotchbroker.btlp.dispatch;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver-facing endpoints for responding to assignments. Secured to {@code DRIVER} and {@code ADMIN}
 * via the {@code /api/v1/driver/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/driver/assignments")
public class DriverAssignmentController {

  private static final Logger log = LoggerFactory.getLogger(DriverAssignmentController.class);

  private final AssignmentService assignmentService;

  public DriverAssignmentController(AssignmentService assignmentService) {
    this.assignmentService = assignmentService;
  }

  @GetMapping("/{id}")
  public AssignmentResponse getById(@PathVariable UUID id) {
    return assignmentService.getById(id);
  }

  @PostMapping("/{id}/accept")
  public AssignmentResponse accept(@PathVariable UUID id) {
    log.info("Accepting assignment id={}", id);
    return assignmentService.accept(id);
  }

  @PostMapping("/{id}/reject")
  public AssignmentResponse reject(@PathVariable UUID id) {
    log.info("Rejecting assignment id={}", id);
    return assignmentService.reject(id);
  }

  @PostMapping("/{id}/complete")
  public AssignmentResponse complete(@PathVariable UUID id) {
    log.info("Completing assignment id={}", id);
    return assignmentService.complete(id);
  }
}
