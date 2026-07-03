package com.topnotchbroker.btlp.load;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Dispatcher-facing load create/read/update endpoints. */
@RestController
@RequestMapping("/api/v1/loads")
public class LoadController {

  private static final Logger log = LoggerFactory.getLogger(LoadController.class);
  private static final int MAX_PAGE_SIZE = 100;

  private final LoadService loadService;

  public LoadController(LoadService loadService) {
    this.loadService = loadService;
  }

  @PostMapping
  public ResponseEntity<LoadResponse> create(
      @Valid @RequestBody LoadCreateRequest request,
      Authentication authentication,
      UriComponentsBuilder uriBuilder) {
    LoadResponse created = loadService.create(request, authentication.getName());
    URI location = uriBuilder.path("/api/v1/loads/{id}").buildAndExpand(created.id()).toUri();
    log.info("Created load id={} by={}", created.id(), authentication.getName());
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}")
  public LoadResponse getById(@PathVariable UUID id) {
    return loadService.getById(id);
  }

  @GetMapping
  public PagedResponse<LoadResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return loadService.list(safePage, safeSize);
  }

  @PutMapping("/{id}")
  public LoadResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody LoadUpdateRequest request,
      Authentication authentication) {
    log.info("Updating load id={} by={}", id, authentication.getName());
    return loadService.update(id, request, authentication.getName());
  }
}
