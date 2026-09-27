package com.topnotchbroker.btlp.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response view of an audit event. */
public record AuditEventResponse(
    UUID id,
    long sequence,
    AuditEntityType entityType,
    UUID entityId,
    AuditAction action,
    String detail,
    String actor,
    OffsetDateTime occurredAt) {

  static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.id(),
        event.sequence(),
        event.entityType(),
        event.entityId(),
        event.action(),
        event.detail(),
        event.actor(),
        event.occurredAt());
  }
}
