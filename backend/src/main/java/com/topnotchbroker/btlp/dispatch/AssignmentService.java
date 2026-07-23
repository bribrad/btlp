package com.topnotchbroker.btlp.dispatch;

import com.topnotchbroker.btlp.audit.AuditAction;
import com.topnotchbroker.btlp.audit.AuditEntityType;
import com.topnotchbroker.btlp.audit.AuditService;
import com.topnotchbroker.btlp.driver.DriverAvailability;
import com.topnotchbroker.btlp.driver.DriverRepository;
import com.topnotchbroker.btlp.idempotency.IdempotencyService;
import com.topnotchbroker.btlp.job.JobRepository;
import com.topnotchbroker.btlp.job.JobStatus;
import com.topnotchbroker.btlp.web.InvalidStateTransitionException;
import com.topnotchbroker.btlp.web.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application logic for driver responses to assignments (accept / reject / complete) and the
 * timeout-driven expiration sweep. Each successful transition cascades the corresponding job-status
 * and driver-availability changes and records an audit entry, all within a single transaction.
 *
 * <p>State transitions use a guarded conditional UPDATE in {@link AssignmentRepository} so a
 * concurrent request cannot apply the same change twice; the pre-checks here exist to return precise
 * {@code 404} vs. {@code 409} responses.
 */
@Service
public class AssignmentService {

  private final AssignmentRepository assignmentRepository;
  private final JobRepository jobRepository;
  private final DriverRepository driverRepository;
  private final AuditService auditService;
  private final IdempotencyService idempotency;

  public AssignmentService(
      AssignmentRepository assignmentRepository,
      JobRepository jobRepository,
      DriverRepository driverRepository,
      AuditService auditService,
      IdempotencyService idempotency) {
    this.assignmentRepository = assignmentRepository;
    this.jobRepository = jobRepository;
    this.driverRepository = driverRepository;
    this.auditService = auditService;
    this.idempotency = idempotency;
  }

  @Transactional(readOnly = true)
  public AssignmentResponse getById(UUID id) {
    return AssignmentResponse.from(
        assignmentRepository.findById(id).orElseThrow(() -> notFound(id)));
  }

  /**
   * Driver accepts a pending assignment: assignment &rarr; ACCEPTED, job &rarr; ASSIGNED, driver
   * &rarr; ON_TRIP. Rejects an assignment whose acceptance window has already elapsed. Idempotent
   * when an {@code Idempotency-Key} is supplied.
   */
  @Transactional
  public AssignmentResponse accept(UUID id, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey, "assignment:accept:" + id, AssignmentResponse.class, () -> doAccept(id));
  }

  private AssignmentResponse doAccept(UUID id) {
    Assignment current = assignmentRepository.findById(id).orElseThrow(() -> notFound(id));
    requireTransition(current, AssignmentState.ACCEPTED);
    if (isPastDeadline(current)) {
      throw new InvalidStateTransitionException(
          "Assignment " + id + " has expired and can no longer be accepted.");
    }
    Assignment accepted = assignmentRepository.accept(id).orElseThrow(() -> concurrentlyChanged(id));
    jobRepository.updateStatus(accepted.jobId(), JobStatus.ASSIGNED);
    driverRepository.updateAvailability(accepted.driverId(), DriverAvailability.ON_TRIP);
    auditService.record(AuditEntityType.JOB, accepted.jobId(), AuditAction.UPDATE);
    auditService.record(AuditEntityType.ASSIGNMENT, accepted.id(), AuditAction.UPDATE);
    return AssignmentResponse.from(accepted);
  }

  /**
   * Driver rejects a pending assignment: assignment &rarr; REJECTED; the job stays actionable.
   * Idempotent when an {@code Idempotency-Key} is supplied.
   */
  @Transactional
  public AssignmentResponse reject(UUID id, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey, "assignment:reject:" + id, AssignmentResponse.class, () -> doReject(id));
  }

  private AssignmentResponse doReject(UUID id) {
    Assignment current = assignmentRepository.findById(id).orElseThrow(() -> notFound(id));
    requireTransition(current, AssignmentState.REJECTED);
    Assignment rejected = assignmentRepository.reject(id).orElseThrow(() -> concurrentlyChanged(id));
    auditService.record(AuditEntityType.ASSIGNMENT, rejected.id(), AuditAction.UPDATE);
    return AssignmentResponse.from(rejected);
  }

  /**
   * Driver completes an accepted assignment: assignment &rarr; COMPLETED, job &rarr; COMPLETED,
   * driver &rarr; AVAILABLE. Idempotent when an {@code Idempotency-Key} is supplied.
   */
  @Transactional
  public AssignmentResponse complete(UUID id, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey, "assignment:complete:" + id, AssignmentResponse.class, () -> doComplete(id));
  }

  private AssignmentResponse doComplete(UUID id) {
    Assignment current = assignmentRepository.findById(id).orElseThrow(() -> notFound(id));
    requireTransition(current, AssignmentState.COMPLETED);
    Assignment completed =
        assignmentRepository.complete(id).orElseThrow(() -> concurrentlyChanged(id));
    jobRepository.updateStatus(completed.jobId(), JobStatus.COMPLETED);
    driverRepository.updateAvailability(completed.driverId(), DriverAvailability.AVAILABLE);
    auditService.record(AuditEntityType.JOB, completed.jobId(), AuditAction.UPDATE);
    auditService.record(AuditEntityType.ASSIGNMENT, completed.id(), AuditAction.UPDATE);
    return AssignmentResponse.from(completed);
  }

  /**
   * Expires every pending assignment past its deadline. The associated jobs remain {@code
   * UNASSIGNED} while pending, so they are already back in the actionable queue once expired. Runs
   * on a schedule and is safe to invoke directly (e.g. from tests). Returns the number expired.
   */
  @Transactional
  public int expireStaleAssignments() {
    List<Assignment> expired = assignmentRepository.expireStale();
    for (Assignment assignment : expired) {
      auditService.record(AuditEntityType.ASSIGNMENT, assignment.id(), AuditAction.UPDATE);
    }
    return expired.size();
  }

  private static boolean isPastDeadline(Assignment assignment) {
    return assignment.expiresAt() != null && !assignment.expiresAt().isAfter(OffsetDateTime.now());
  }

  private static void requireTransition(Assignment assignment, AssignmentState target) {
    if (!assignment.state().canTransitionTo(target)) {
      throw new InvalidStateTransitionException(
          "Assignment "
              + assignment.id()
              + " cannot transition from "
              + assignment.state()
              + " to "
              + target
              + ".");
    }
  }

  private static InvalidStateTransitionException concurrentlyChanged(UUID id) {
    return new InvalidStateTransitionException(
        "Assignment " + id + " changed state concurrently; please retry.");
  }

  private static ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Assignment not found: " + id);
  }
}
