package com.topnotchbroker.btlp.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {

  private static final Logger log = LoggerFactory.getLogger(OperationsController.class);

  @GetMapping("/me")
  public Map<String, Object> me(Authentication authentication) {
    log.info("GET /me user={}", authentication.getName());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("username", authentication.getName());
    payload.put(
        "authorities",
        authentication.getAuthorities().stream()
            .map(Object::toString)
            .sorted()
            .collect(Collectors.toList()));
    return payload;
  }

  @GetMapping("/dispatch/jobs")
  public Map<String, String> dispatchJobs(Authentication authentication) {
    log.info("GET /dispatch/jobs user={}", authentication.getName());
    return Map.of("message", "Dispatch queue access granted.");
  }

  @GetMapping("/driver/assignments")
  public Map<String, String> driverAssignments(Authentication authentication) {
    log.info("GET /driver/assignments user={}", authentication.getName());
    return Map.of("message", "Driver assignment access granted.");
  }

  @GetMapping("/billing/exports")
  public Map<String, String> billingExports(Authentication authentication) {
    log.info("GET /billing/exports user={}", authentication.getName());
    return Map.of("message", "Billing export access granted.");
  }

  @GetMapping("/admin/users")
  public Map<String, String> adminUsers(Authentication authentication) {
    log.info("GET /admin/users user={}", authentication.getName());
    return Map.of("message", "Administrative access granted.");
  }
}
