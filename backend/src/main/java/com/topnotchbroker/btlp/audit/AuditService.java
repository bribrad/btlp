package com.topnotchbroker.btlp.audit;

import com.topnotchbroker.btlp.web.PagedResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and retrieves audit-trail entries. {@link #record} resolves the acting user from the
 * security context and, running with the default {@code REQUIRED} propagation, joins the caller's
 * transaction so the audit entry is committed atomically with the change it describes.
 */
@Service
public class AuditService {

  private static final String SYSTEM_ACTOR = "system";

  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void record(AuditEntityType entityType, UUID entityId, AuditAction action) {
    repository.insert(entityType, entityId, action, currentActor());
  }

  @Transactional(readOnly = true)
  public PagedResponse<AuditEventResponse> list(
      AuditEntityType entityType, UUID entityId, int page, int size) {
    int offset = page * size;
    List<AuditEventResponse> content =
        repository.findPage(entityType, entityId, size, offset).stream()
            .map(AuditEventResponse::from)
            .toList();
    long total = repository.count(entityType, entityId);
    return PagedResponse.of(content, page, size, total);
  }

  private static String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
      return authentication.getName();
    }
    return SYSTEM_ACTOR;
  }
}
