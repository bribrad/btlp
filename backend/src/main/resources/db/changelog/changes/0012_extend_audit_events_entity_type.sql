--liquibase formatted sql logicalFilePath:db/changelog/changes/0012_extend_audit_events_entity_type.sql

--changeset btlp:0012-extend-audit-events-entity-type
ALTER TABLE audit_events DROP CONSTRAINT audit_events_entity_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_entity_type_check
    CHECK (entity_type IN ('LOAD', 'JOB', 'ASSIGNMENT'));
--rollback ALTER TABLE audit_events DROP CONSTRAINT audit_events_entity_type_check; ALTER TABLE audit_events ADD CONSTRAINT audit_events_entity_type_check CHECK (entity_type IN ('LOAD', 'JOB'));
