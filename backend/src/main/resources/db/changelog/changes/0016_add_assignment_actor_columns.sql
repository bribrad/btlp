--liquibase formatted sql logicalFilePath:db/changelog/changes/0016_add_assignment_actor_columns.sql

--changeset btlp:0016-add-assignment-actor-columns
ALTER TABLE assignments ADD COLUMN created_by VARCHAR(150);
ALTER TABLE assignments ADD COLUMN updated_by VARCHAR(150);
--rollback ALTER TABLE assignments DROP COLUMN updated_by;
--rollback ALTER TABLE assignments DROP COLUMN created_by;
