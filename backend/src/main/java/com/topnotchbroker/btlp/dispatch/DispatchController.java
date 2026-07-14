package com.topnotchbroker.btlp.dispatch;

import jakarta.validation.Valid;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Dispatcher-facing endpoint for creating driver assignments. Secured to {@code DISPATCHER} and
 * {@code ADMIN} roles via the {@code /api/v1/dispatch/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/dispatch/assignments")
public class DispatchController {

  private static final Logger log = LoggerFactory.getLogger(DispatchController.class);

  private final DispatchService dispatchService;

  public DispatchController(DispatchService dispatchService) {
    this.dispatchService = dispatchService;
  }

  @PostMapping
  public ResponseEntity<AssignmentResponse> dispatch(
      @Valid @RequestBody DispatchRequest request, UriComponentsBuilder uriBuilder) {
    AssignmentResponse created = dispatchService.dispatch(request);
    URI location =
        uriBuilder
            .path("/api/v1/dispatch/assignments/{id}")
            .buildAndExpand(created.id())
            .toUri();
    log.info(
        "Dispatched assignment id={} jobId={} driverId={} state={}",
        created.id(),
        created.jobId(),
        created.driverId(),
        created.state());
    return ResponseEntity.created(location).body(created);
  }
}
