--liquibase formatted sql logicalFilePath:db/changelog/changes/0001_create_drivers.sql

--changeset btlp:0001-create-drivers
CREATE TABLE drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(40),
    license_number VARCHAR(100),
    availability VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CONSTRAINT drivers_availability_check CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE', 'ON_TRIP')),
    app_device_tokens JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE drivers;
