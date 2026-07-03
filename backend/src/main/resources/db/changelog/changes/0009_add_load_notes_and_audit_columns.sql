--liquibase formatted sql logicalFilePath:db/changelog/changes/0009_add_load_notes_and_audit_columns.sql

--changeset btlp:0009-add-load-notes-and-audit-columns
ALTER TABLE loads ADD COLUMN notes TEXT;
ALTER TABLE loads ADD COLUMN created_by VARCHAR(150);
ALTER TABLE loads ADD COLUMN updated_by VARCHAR(150);
--rollback ALTER TABLE loads DROP COLUMN notes, DROP COLUMN created_by, DROP COLUMN updated_by;
