--liquibase formatted sql logicalFilePath:db/changelog/changes/0011_add_driver_status.sql

--changeset btlp:0011-add-driver-status
ALTER TABLE drivers ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CONSTRAINT drivers_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));
CREATE INDEX idx_drivers_status_availability ON drivers (status, availability);
--rollback DROP INDEX idx_drivers_status_availability;
--rollback ALTER TABLE drivers DROP COLUMN status;
