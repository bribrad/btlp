--liquibase formatted sql logicalFilePath:db/changelog/changes/0004_create_assignments.sql

--changeset btlp:0004-create-assignments
CREATE TABLE assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL
        CONSTRAINT assignments_job_id_fkey REFERENCES jobs (id) ON DELETE RESTRICT,
    driver_id UUID NOT NULL
        CONSTRAINT assignments_driver_id_fkey REFERENCES drivers (id) ON DELETE RESTRICT,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT assignments_state_check CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELED')),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_assignments_job_id ON assignments (job_id);
CREATE INDEX idx_assignments_driver_id ON assignments (driver_id);
--rollback DROP TABLE assignments;
