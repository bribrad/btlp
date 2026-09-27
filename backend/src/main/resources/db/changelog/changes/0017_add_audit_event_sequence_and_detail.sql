--liquibase formatted sql logicalFilePath:db/changelog/changes/0017_add_audit_event_sequence_and_detail.sql

--changeset btlp:0017-add-audit-event-sequence-and-detail
-- occurred_at defaults to now(), which in PostgreSQL is transaction start time, so every event a
-- single dispatch action writes shares one timestamp. seq is allocated per INSERT and therefore
-- orders those events the way they actually happened, identically on every read.
ALTER TABLE audit_events ADD COLUMN detail VARCHAR(200);
CREATE SEQUENCE audit_events_seq_seq;
ALTER TABLE audit_events ADD COLUMN seq BIGINT;
UPDATE audit_events e
SET seq = ordered.position
FROM (
    SELECT id, row_number() OVER (ORDER BY occurred_at, id) AS position
    FROM audit_events
) ordered
WHERE e.id = ordered.id;
SELECT setval('audit_events_seq_seq', COALESCE((SELECT max(seq) FROM audit_events), 0) + 1, false);
ALTER TABLE audit_events ALTER COLUMN seq SET DEFAULT nextval('audit_events_seq_seq');
ALTER TABLE audit_events ALTER COLUMN seq SET NOT NULL;
ALTER SEQUENCE audit_events_seq_seq OWNED BY audit_events.seq;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_seq_key UNIQUE (seq);
--rollback ALTER TABLE audit_events DROP CONSTRAINT audit_events_seq_key;
--rollback ALTER TABLE audit_events DROP COLUMN seq;
--rollback DROP SEQUENCE IF EXISTS audit_events_seq_seq;
--rollback ALTER TABLE audit_events DROP COLUMN detail;
