package com.topnotchbroker.btlp.dispatch;

import com.topnotchbroker.btlp.web.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Dispatcher-facing endpoints for the dispatch board and the assignments it drives. Secured to
 * {@code DISPATCHER} and {@code ADMIN} roles via the {@code /api/v1/dispatch/**} rule in {@code
 * SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

  private static final Logger log = LoggerFactory.getLogger(DispatchController.class);
  private static final int MAX_PAGE_SIZE = 100;

  private final DispatchService dispatchService;

  public DispatchController(DispatchService dispatchService) {
    this.dispatchService = dispatchService;
  }

  /**
   * The dispatch queue, newest-needed first, optionally narrowed to one lane. Each entry carries
   * the job, its load endpoints, and the active assignment with its driver.
   */
  @GetMapping("/board")
  public PagedResponse<DispatchBoardEntry> board(
      @RequestParam(required = false) DispatchBoardLane lane,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return dispatchService.board(lane, Math.max(page, 0), clampSize(size));
  }

  @PostMapping("/assignments")
  public ResponseEntity<AssignmentResponse> dispatch(
      @Valid @RequestBody DispatchRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      Authentication authentication,
      UriComponentsBuilder uriBuilder) {
    AssignmentResponse created =
        dispatchService.dispatch(request, authentication.getName(), idempotencyKey);
    log.info(
        "Dispatched assignment id={} jobId={} driverId={} state={} by={}",
        created.id(),
        created.jobId(),
        created.driverId(),
        created.state(),
        authentication.getName());
    return ResponseEntity.created(locationOf(uriBuilder, created.id())).body(created);
  }

  /** Assignments newest-first; scope to a job for its full dispatch and reassignment history. */
  @GetMapping("/assignments")
  public PagedResponse<AssignmentResponse> list(
      @RequestParam(required = false) UUID jobId,
      @RequestParam(required = false) UUID driverId,
      @RequestParam(required = false) AssignmentState state,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return dispatchService.list(jobId, driverId, state, Math.max(page, 0), clampSize(size));
  }

  @GetMapping("/assignments/{id}")
  public AssignmentResponse getById(@PathVariable UUID id) {
    return dispatchService.getById(id);
  }

  @PostMapping("/assignments/{id}/cancel")
  public AssignmentResponse cancel(
      @PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      Authentication authentication) {
    log.info("Canceling assignment id={} by={}", id, authentication.getName());
    return dispatchService.cancel(id, authentication.getName(), idempotencyKey);
  }

  /** Cancels the current assignment and dispatches a new one, returning the new assignment. */
  @PostMapping("/assignments/{id}/reassign")
  public ResponseEntity<AssignmentResponse> reassign(
      @PathVariable UUID id,
      @Valid @RequestBody ReassignRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      Authentication authentication,
      UriComponentsBuilder uriBuilder) {
    AssignmentResponse created =
        dispatchService.reassign(id, request, authentication.getName(), idempotencyKey);
    log.info(
        "Reassigned assignment id={} to assignment id={} driverId={} by={}",
        id,
        created.id(),
        created.driverId(),
        authentication.getName());
    return ResponseEntity.created(locationOf(uriBuilder, created.id())).body(created);
  }

  private static URI locationOf(UriComponentsBuilder uriBuilder, UUID assignmentId) {
    return uriBuilder
        .path("/api/v1/dispatch/assignments/{id}")
        .buildAndExpand(assignmentId)
        .toUri();
  }

  private static int clampSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
