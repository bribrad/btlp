--liquibase formatted sql logicalFilePath:db/changelog/changes/0006_create_export_runs.sql

--changeset btlp:0006-create-export-runs
CREATE TABLE export_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    filter_params JSONB,
    format VARCHAR(10) NOT NULL
        CONSTRAINT export_runs_format_check CHECK (format IN ('CSV', 'JSON')),
    created_by VARCHAR(150) NOT NULL,
    file_uri VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT export_runs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE export_runs;
