package com.topnotchbroker.btlp.audit;

import com.topnotchbroker.btlp.web.PagedResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit-trail retrieval endpoint (ADMIN-only, enforced in {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

  private static final int MAX_PAGE_SIZE = 100;

  private final AuditService auditService;

  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping
  public PagedResponse<AuditEventResponse> list(
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return auditService.list(entityType, entityId, safePage, safeSize);
  }
}
