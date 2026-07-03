package com.topnotchbroker.btlp.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of an audit event. */
public record AuditEventResponse(
    UUID id,
    AuditEntityType entityType,
    UUID entityId,
    AuditAction action,
    String actor,
    OffsetDateTime occurredAt) {

  static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.id(),
        event.entityType(),
        event.entityId(),
        event.action(),
        event.actor(),
        event.occurredAt());
  }
}
