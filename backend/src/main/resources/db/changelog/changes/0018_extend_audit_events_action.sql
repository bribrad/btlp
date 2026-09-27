--liquibase formatted sql logicalFilePath:db/changelog/changes/0018_extend_audit_events_action.sql

--changeset btlp:0018-extend-audit-events-action
ALTER TABLE audit_events DROP CONSTRAINT audit_events_action_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_action_check
    CHECK (action IN ('CREATE', 'UPDATE', 'STATUS_CHANGE', 'ASSIGN', 'REASSIGN', 'CANCEL',
                      'ACCEPT', 'REJECT', 'EXPIRE', 'COMPLETE'));
--rollback ALTER TABLE audit_events DROP CONSTRAINT audit_events_action_check; ALTER TABLE audit_events ADD CONSTRAINT audit_events_action_check CHECK (action IN ('CREATE', 'UPDATE'));
