--liquibase formatted sql logicalFilePath:db/changelog/changes/0003_create_jobs.sql

--changeset btlp:0003-create-jobs
CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_id UUID NOT NULL
        CONSTRAINT jobs_load_id_fkey REFERENCES loads (id) ON DELETE RESTRICT,
    job_type VARCHAR(20) NOT NULL
        CONSTRAINT jobs_job_type_check CHECK (job_type IN ('PICKUP', 'DROPOFF')),
    sequence INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNASSIGNED'
        CONSTRAINT jobs_status_check CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED')),
    scheduled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT jobs_load_sequence_key UNIQUE (load_id, sequence)
);
CREATE INDEX idx_jobs_load_id ON jobs (load_id);
--rollback DROP TABLE jobs;
