-- V13__create_event_attachments.sql — SCRUM-148 (Décision P).
--
-- Backs POST /events/{id}/attachments + DELETE /events/{id}/attachments/{aid}.
--
-- FK event_id -> events(id) ON DELETE CASCADE : local FK (both tables in
-- postgres-event). Hard-delete of an event auto-purges its attachment rows ;
-- the S3 objects are cleaned up separately by EventService.delete()
-- (best-effort, hors-transaction — Décision T).
--
-- CHECK constraints :
--   - event_attachments_size_check : max 10 MiB = 10 * 1024 * 1024 = 10485760
--     bytes.
--   - event_attachments_mime_check  : whitelist PDF / DOC / DOCX / XLSX
--     (4 MIME types).
-- These are the LAST LINE OF DEFENSE — the service layer rejects with 422
-- envelopes first (Décision V).
--
-- No UNIQUE on (event_id, file_name) — duplicates accepted (Décision P
-- rationale : a user may legitimately upload presentation.pdf v1 then v2 ;
-- the front-end disambiguates with file_size + uploaded_at).
--
-- Immutable post-push (piège #12 — Flyway migrations are append-only after
-- the branch is shared).

CREATE SEQUENCE IF NOT EXISTS event_attachments_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS event_attachments (
    id              BIGINT       NOT NULL DEFAULT nextval('event_attachments_seq'),
    event_id        BIGINT       NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_url        TEXT         NOT NULL,
    file_size       BIGINT       NOT NULL,
    mime_type       VARCHAR(128) NOT NULL,
    uploaded_by_id  UUID         NOT NULL,
    uploaded_at     TIMESTAMP    NOT NULL,
    CONSTRAINT pk_event_attachments PRIMARY KEY (id),
    CONSTRAINT fk_event_attachment_event FOREIGN KEY (event_id)
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT event_attachments_size_check CHECK (file_size <= 10485760),
    CONSTRAINT event_attachments_mime_check CHECK (
        mime_type IN (
            'application/pdf',
            'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_event_attachment_event ON event_attachments(event_id);
