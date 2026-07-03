package com.topnotchbroker.btlp.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable domain representation of a row in the {@code audit_events} table. */
public record AuditEvent(
    UUID id,
    AuditEntityType entityType,
    UUID entityId,
    AuditAction action,
    String actor,
    OffsetDateTime occurredAt) {}
