--liquibase formatted sql logicalFilePath:db/changelog/changes/0014_extend_assignments_state.sql

--changeset btlp:0014-extend-assignments-state
ALTER TABLE assignments DROP CONSTRAINT assignments_state_check;
ALTER TABLE assignments ADD CONSTRAINT assignments_state_check
    CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELED', 'COMPLETED'));
--rollback ALTER TABLE assignments DROP CONSTRAINT assignments_state_check; ALTER TABLE assignments ADD CONSTRAINT assignments_state_check CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELED'));
