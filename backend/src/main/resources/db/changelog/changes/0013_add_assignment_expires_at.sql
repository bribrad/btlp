--liquibase formatted sql logicalFilePath:db/changelog/changes/0013_add_assignment_expires_at.sql

--changeset btlp:0013-add-assignment-expires-at
ALTER TABLE assignments ADD COLUMN expires_at TIMESTAMPTZ;
CREATE INDEX idx_assignments_state_expires_at ON assignments (state, expires_at);
--rollback DROP INDEX idx_assignments_state_expires_at;
--rollback ALTER TABLE assignments DROP COLUMN expires_at;
