package com.topnotchbroker.btlp.job;

import com.topnotchbroker.btlp.web.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Dispatcher-facing job create/read/update endpoints, linked to a parent load. */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

  private static final Logger log = LoggerFactory.getLogger(JobController.class);
  private static final int MAX_PAGE_SIZE = 100;

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  @PostMapping
  public ResponseEntity<JobResponse> create(
      @Valid @RequestBody JobCreateRequest request, UriComponentsBuilder uriBuilder) {
    JobResponse created = jobService.create(request);
    URI location = uriBuilder.path("/api/v1/jobs/{id}").buildAndExpand(created.id()).toUri();
    log.info(
        "Created job id={} loadId={} sequence={}",
        created.id(),
        created.loadId(),
        created.sequence());
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}")
  public JobResponse getById(@PathVariable UUID id) {
    return jobService.getById(id);
  }

  @GetMapping
  public PagedResponse<JobResponse> list(
      @RequestParam(required = false) UUID loadId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return jobService.list(loadId, safePage, safeSize);
  }

  @PutMapping("/{id}")
  public JobResponse update(@PathVariable UUID id, @Valid @RequestBody JobUpdateRequest request) {
    log.info("Updating job id={}", id);
    return jobService.update(id, request);
  }
}
