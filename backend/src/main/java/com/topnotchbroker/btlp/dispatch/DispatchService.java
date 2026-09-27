package com.topnotchbroker.btlp.dispatch;

import com.topnotchbroker.btlp.audit.AuditAction;
import com.topnotchbroker.btlp.audit.AuditEntityType;
import com.topnotchbroker.btlp.audit.AuditService;
import com.topnotchbroker.btlp.driver.DriverAvailability;
import com.topnotchbroker.btlp.driver.DriverRepository;
import com.topnotchbroker.btlp.idempotency.IdempotencyService;
import com.topnotchbroker.btlp.job.JobRepository;
import com.topnotchbroker.btlp.job.JobStatus;
import com.topnotchbroker.btlp.web.ConflictException;
import com.topnotchbroker.btlp.web.InvalidStateTransitionException;
import com.topnotchbroker.btlp.web.PagedResponse;
import com.topnotchbroker.btlp.web.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application logic for the dispatcher side of the assignment lifecycle: dispatching a driver to a
 * job, pulling that assignment back, moving it to another driver, and reading the board those
 * actions drive. Every mutation records an audit entry capturing the actor and stamps the acting
 * dispatcher onto the assignment row, so a reassignment leaves a readable trail.
 */
@Service
public class DispatchService {

  private final AssignmentRepository assignmentRepository;
  private final DispatchBoardRepository boardRepository;
  private final JobRepository jobRepository;
  private final DriverRepository driverRepository;
  private final AuditService auditService;
  private final DispatchProperties properties;
  private final IdempotencyService idempotency;

  public DispatchService(
      AssignmentRepository assignmentRepository,
      DispatchBoardRepository boardRepository,
      JobRepository jobRepository,
      DriverRepository driverRepository,
      AuditService auditService,
      DispatchProperties properties,
      IdempotencyService idempotency) {
    this.assignmentRepository = assignmentRepository;
    this.boardRepository = boardRepository;
    this.jobRepository = jobRepository;
    this.driverRepository = driverRepository;
    this.auditService = auditService;
    this.properties = properties;
    this.idempotency = idempotency;
  }

  @Transactional
  public AssignmentResponse dispatch(DispatchRequest request, String actor, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey,
        "dispatch:create:" + request.jobId() + ":" + request.driverId(),
        AssignmentResponse.class,
        () -> doDispatch(request, actor));
  }

  private AssignmentResponse doDispatch(DispatchRequest request, String actor) {
    requireJob(request.jobId());
    requireDriver(request.driverId());
    if (assignmentRepository.hasActiveAssignment(request.jobId())) {
      throw new ConflictException(
          "Job " + request.jobId() + " already has a pending or accepted assignment.");
    }
    Assignment created = insertAssignment(request.jobId(), request.driverId(), actor);
    auditService.record(
        AuditEntityType.ASSIGNMENT, created.id(), AuditAction.ASSIGN, created.state().name());
    return AssignmentResponse.from(created);
  }

  /**
   * Pulls an active assignment back: assignment &rarr; CANCELED and, when the driver had already
   * accepted, job &rarr; UNASSIGNED and driver &rarr; AVAILABLE so both return to the board.
   * Idempotent when an {@code Idempotency-Key} is supplied.
   */
  @Transactional
  public AssignmentResponse cancel(UUID id, String actor, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey,
        "dispatch:cancel:" + id,
        AssignmentResponse.class,
        () -> AssignmentResponse.from(doCancel(id, actor)));
  }

  /**
   * Moves a job to another driver in one step: the current assignment is canceled and a fresh
   * {@code PENDING} assignment is dispatched to {@code driverId}. Re-dispatching to the same driver
   * is allowed and simply restarts their acceptance window. Idempotent when an {@code
   * Idempotency-Key} is supplied.
   */
  @Transactional
  public AssignmentResponse reassign(
      UUID id, ReassignRequest request, String actor, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey,
        "dispatch:reassign:" + id + ":" + request.driverId(),
        AssignmentResponse.class,
        () -> doReassign(id, request, actor));
  }

  private AssignmentResponse doReassign(UUID id, ReassignRequest request, String actor) {
    requireDriver(request.driverId());
    Assignment canceled = doCancel(id, actor);
    Assignment created = insertAssignment(canceled.jobId(), request.driverId(), actor);
    auditService.record(
        AuditEntityType.ASSIGNMENT, created.id(), AuditAction.REASSIGN, created.state().name());
    return AssignmentResponse.from(created);
  }

  /**
   * Cancels the assignment and cascades the job/driver side effects, recording an audit entry for
   * each row it touches. Shared by {@link #cancel} and {@link #reassign} so both leave the same
   * trail.
   */
  private Assignment doCancel(UUID id, String actor) {
    Assignment current =
        assignmentRepository.findById(id).orElseThrow(() -> notFound(id));
    if (!current.state().canTransitionTo(AssignmentState.CANCELED)) {
      throw new InvalidStateTransitionException(
          "Assignment " + id + " cannot be canceled from state " + current.state() + ".");
    }
    Assignment canceled =
        assignmentRepository
            .cancel(id, actor)
            .orElseThrow(
                () ->
                    new InvalidStateTransitionException(
                        "Assignment " + id + " changed state concurrently; please retry."));
    if (current.state() == AssignmentState.ACCEPTED) {
      jobRepository.updateStatus(canceled.jobId(), JobStatus.UNASSIGNED);
      driverRepository.updateAvailability(canceled.driverId(), DriverAvailability.AVAILABLE);
      auditService.record(
          AuditEntityType.JOB,
          canceled.jobId(),
          AuditAction.STATUS_CHANGE,
          JobStatus.UNASSIGNED.name());
    }
    auditService.record(
        AuditEntityType.ASSIGNMENT, canceled.id(), AuditAction.CANCEL, canceled.state().name());
    return canceled;
  }

  @Transactional(readOnly = true)
  public AssignmentResponse getById(UUID id) {
    return AssignmentResponse.from(
        assignmentRepository.findById(id).orElseThrow(() -> notFound(id)));
  }

  /** Assignments newest-first, optionally scoped to a job, a driver, and/or a single state. */
  @Transactional(readOnly = true)
  public PagedResponse<AssignmentResponse> list(
      UUID jobId, UUID driverId, AssignmentState state, int page, int size) {
    int offset = page * size;
    List<AssignmentResponse> content =
        assignmentRepository.findPage(jobId, driverId, state, size, offset).stream()
            .map(AssignmentResponse::from)
            .toList();
    long total = assignmentRepository.count(jobId, driverId, state);
    return PagedResponse.of(content, page, size, total);
  }

  /** The dispatch queue: every actionable job with its active assignment and driver, if any. */
  @Transactional(readOnly = true)
  public PagedResponse<DispatchBoardEntry> board(DispatchBoardLane lane, int page, int size) {
    int offset = page * size;
    List<DispatchBoardEntry> content = boardRepository.findPage(lane, size, offset);
    long total = boardRepository.count(lane);
    return PagedResponse.of(content, page, size, total);
  }

  private Assignment insertAssignment(UUID jobId, UUID driverId, String actor) {
    OffsetDateTime expiresAt = OffsetDateTime.now().plus(properties.assignmentTimeout());
    return assignmentRepository.insert(jobId, driverId, expiresAt, actor);
  }

  private void requireJob(UUID jobId) {
    if (!jobRepository.existsById(jobId)) {
      throw new ResourceNotFoundException("Job not found: " + jobId);
    }
  }

  private void requireDriver(UUID driverId) {
    if (driverRepository.findById(driverId).isEmpty()) {
      throw new ResourceNotFoundException("Driver not found: " + driverId);
    }
  }

  private static ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Assignment not found: " + id);
  }
}
