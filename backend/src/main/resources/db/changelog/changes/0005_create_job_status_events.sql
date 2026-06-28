--liquibase formatted sql logicalFilePath:db/changelog/changes/0005_create_job_status_events.sql

--changeset btlp:0005-create-job-status-events
CREATE TABLE job_status_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL
        CONSTRAINT job_status_events_job_id_fkey REFERENCES jobs (id) ON DELETE RESTRICT,
    driver_id UUID
        CONSTRAINT job_status_events_driver_id_fkey REFERENCES drivers (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL
        CONSTRAINT job_status_events_status_check CHECK (status IN ('EN_ROUTE', 'ARRIVED', 'LOADED', 'IN_TRANSIT', 'DELIVERED', 'IN_PROGRESS', 'COMPLETED')),
    event_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    sequence_no BIGINT,
    location VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT job_status_events_job_sequence_key UNIQUE (job_id, sequence_no)
);
CREATE INDEX idx_job_status_events_job_id ON job_status_events (job_id);
--rollback DROP TABLE job_status_events;
