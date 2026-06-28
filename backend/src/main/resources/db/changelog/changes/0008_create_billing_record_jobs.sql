--liquibase formatted sql logicalFilePath:db/changelog/changes/0008_create_billing_record_jobs.sql

--changeset btlp:0008-create-billing-record-jobs
CREATE TABLE billing_record_jobs (
    billing_record_id UUID NOT NULL
        CONSTRAINT billing_record_jobs_billing_record_id_fkey REFERENCES billing_records (id) ON DELETE CASCADE,
    job_id UUID NOT NULL
        CONSTRAINT billing_record_jobs_job_id_fkey REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT billing_record_jobs_pkey PRIMARY KEY (billing_record_id, job_id)
);
CREATE INDEX idx_billing_record_jobs_job_id ON billing_record_jobs (job_id);
--rollback DROP TABLE billing_record_jobs;
