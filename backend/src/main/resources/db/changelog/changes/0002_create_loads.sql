--liquibase formatted sql logicalFilePath:db/changelog/changes/0002_create_loads.sql

--changeset btlp:0002-create-loads
CREATE TABLE loads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100),
    origin VARCHAR(500),
    destination VARCHAR(500),
    pickup_window_start TIMESTAMPTZ,
    pickup_window_end TIMESTAMPTZ,
    dropoff_window_start TIMESTAMPTZ,
    dropoff_window_end TIMESTAMPTZ,
    rate_amount NUMERIC(12, 2),
    rate_currency CHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CONSTRAINT loads_status_check CHECK (status IN ('PLANNED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE loads;
