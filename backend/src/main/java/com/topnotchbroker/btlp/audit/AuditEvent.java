package com.topnotchbroker.btlp.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable domain representation of a row in the {@code audit_events} table.
 *
 * @param sequence monotonic insertion order; {@code occurredAt} is transaction-start time and so
 *     ties between the several events one action writes
 * @param detail the audited entity's resulting status/state, or null where the action has none
 */
public record AuditEvent(
    UUID id,
    long sequence,
    AuditEntityType entityType,
    UUID entityId,
    AuditAction action,
    String detail,
    String actor,
    OffsetDateTime occurredAt) {}
