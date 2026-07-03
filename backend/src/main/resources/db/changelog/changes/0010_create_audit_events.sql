--liquibase formatted sql logicalFilePath:db/changelog/changes/0010_create_audit_events.sql

--changeset btlp:0010-create-audit-events
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(20) NOT NULL
        CONSTRAINT audit_events_entity_type_check CHECK (entity_type IN ('LOAD', 'JOB')),
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL
        CONSTRAINT audit_events_action_check CHECK (action IN ('CREATE', 'UPDATE')),
    actor VARCHAR(150) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
--rollback DROP TABLE audit_events;
