--liquibase formatted sql logicalFilePath:db/changelog/changes/0007_create_billing_records.sql

--changeset btlp:0007-create-billing-records
CREATE TABLE billing_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_id UUID NOT NULL
        CONSTRAINT billing_records_load_id_fkey REFERENCES loads (id) ON DELETE RESTRICT,
    billable_amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    ready_at TIMESTAMPTZ,
    export_state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT billing_records_export_state_check CHECK (export_state IN ('PENDING', 'READY', 'EXPORTED', 'FAILED')),
    export_run_id UUID
        CONSTRAINT billing_records_export_run_id_fkey REFERENCES export_runs (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_billing_records_load_id ON billing_records (load_id);
CREATE INDEX idx_billing_records_export_run_id ON billing_records (export_run_id);
--rollback DROP TABLE billing_records;
