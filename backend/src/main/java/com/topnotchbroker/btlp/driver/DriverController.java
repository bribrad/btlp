package com.topnotchbroker.btlp.driver;

import com.topnotchbroker.btlp.web.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Dispatcher-facing driver directory, availability, and assignment-eligibility endpoints. */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

  private static final Logger log = LoggerFactory.getLogger(DriverController.class);
  private static final int MAX_PAGE_SIZE = 100;

  private final DriverService driverService;

  public DriverController(DriverService driverService) {
    this.driverService = driverService;
  }

  @PostMapping
  public ResponseEntity<DriverResponse> create(
      @Valid @RequestBody DriverCreateRequest request, UriComponentsBuilder uriBuilder) {
    DriverResponse created = driverService.create(request);
    URI location = uriBuilder.path("/api/v1/drivers/{id}").buildAndExpand(created.id()).toUri();
    log.info("Created driver id={}", created.id());
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping
  public PagedResponse<DriverResponse> list(
      @RequestParam(required = false) DriverAvailability availability,
      @RequestParam(required = false) DriverStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return driverService.list(availability, status, Math.max(page, 0), clampSize(size));
  }

  @GetMapping("/eligible")
  public PagedResponse<DriverResponse> listEligible(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return driverService.listEligible(Math.max(page, 0), clampSize(size));
  }

  @GetMapping("/{id}")
  public DriverResponse getById(@PathVariable UUID id) {
    return driverService.getById(id);
  }

  @PatchMapping("/{id}/availability")
  public DriverResponse updateAvailability(
      @PathVariable UUID id, @Valid @RequestBody AvailabilityUpdateRequest request) {
    log.info("Updating driver id={} availability={}", id, request.availability());
    return driverService.setAvailability(id, request.availability());
  }

  @PatchMapping("/{id}/status")
  public DriverResponse updateStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
    log.info("Updating driver id={} status={}", id, request.status());
    return driverService.setStatus(id, request.status());
  }

  private static int clampSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
