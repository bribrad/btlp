package com.topnotchbroker.btlp.dispatch;

import com.topnotchbroker.btlp.audit.AuditAction;
import com.topnotchbroker.btlp.audit.AuditEntityType;
import com.topnotchbroker.btlp.audit.AuditService;
import com.topnotchbroker.btlp.driver.DriverRepository;
import com.topnotchbroker.btlp.idempotency.IdempotencyService;
import com.topnotchbroker.btlp.job.JobRepository;
import com.topnotchbroker.btlp.web.ConflictException;
import com.topnotchbroker.btlp.web.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application logic for dispatching a driver to a job. Creates a {@code PENDING} assignment and
 * records an audit entry capturing the actor and assignment context.
 */
@Service
public class DispatchService {

  private final AssignmentRepository assignmentRepository;
  private final JobRepository jobRepository;
  private final DriverRepository driverRepository;
  private final AuditService auditService;
  private final DispatchProperties properties;
  private final IdempotencyService idempotency;

  public DispatchService(
      AssignmentRepository assignmentRepository,
      JobRepository jobRepository,
      DriverRepository driverRepository,
      AuditService auditService,
      DispatchProperties properties,
      IdempotencyService idempotency) {
    this.assignmentRepository = assignmentRepository;
    this.jobRepository = jobRepository;
    this.driverRepository = driverRepository;
    this.auditService = auditService;
    this.properties = properties;
    this.idempotency = idempotency;
  }

  @Transactional
  public AssignmentResponse dispatch(DispatchRequest request, String idempotencyKey) {
    return idempotency.run(
        idempotencyKey,
        "dispatch:create:" + request.jobId() + ":" + request.driverId(),
        AssignmentResponse.class,
        () -> doDispatch(request));
  }

  private AssignmentResponse doDispatch(DispatchRequest request) {
    if (!jobRepository.existsById(request.jobId())) {
      throw new ResourceNotFoundException("Job not found: " + request.jobId());
    }
    if (driverRepository.findById(request.driverId()).isEmpty()) {
      throw new ResourceNotFoundException("Driver not found: " + request.driverId());
    }
    if (assignmentRepository.hasActiveAssignment(request.jobId())) {
      throw new ConflictException(
          "Job " + request.jobId() + " already has a pending or accepted assignment.");
    }
    OffsetDateTime expiresAt = OffsetDateTime.now().plus(properties.assignmentTimeout());
    Assignment created =
        assignmentRepository.insert(request.jobId(), request.driverId(), expiresAt);
    auditService.record(AuditEntityType.ASSIGNMENT, created.id(), AuditAction.CREATE);
    return AssignmentResponse.from(created);
  }

  @Transactional(readOnly = true)
  public AssignmentResponse getById(UUID id) {
    return AssignmentResponse.from(
        assignmentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id)));
  }
}
