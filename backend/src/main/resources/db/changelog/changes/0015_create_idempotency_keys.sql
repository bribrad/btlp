--liquibase formatted sql logicalFilePath:db/changelog/changes/0015_create_idempotency_keys.sql

--changeset btlp:0015-create-idempotency-keys
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(200) NOT NULL
        CONSTRAINT idempotency_keys_key_unique UNIQUE,
    operation VARCHAR(100) NOT NULL,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE idempotency_keys;
