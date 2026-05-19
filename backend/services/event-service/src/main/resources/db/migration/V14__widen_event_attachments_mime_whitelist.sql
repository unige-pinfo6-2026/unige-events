-- V14__widen_event_attachments_mime_whitelist.sql — SCRUM-149 front-end follow-up.
--
-- Widens the V13 `event_attachments_mime_check` constraint to also accept
-- `image/png` and `image/jpeg` so EventEditPage / EventCreatePage can offer
-- image attachments (visuels, captures, posters) alongside the original
-- PDF / DOC / DOCX / XLSX set.
--
-- V13 is already shipped and immutable (piège #12 — Flyway migrations are
-- append-only after the branch is shared) ; this migration drops the old
-- CHECK and re-adds it with the widened whitelist. The Java whitelist
-- (`DocumentFormat.MIME_TO_EXTENSION`) is the source of truth ; this is the
-- last line of defense (Décision V).

ALTER TABLE event_attachments
    DROP CONSTRAINT IF EXISTS event_attachments_mime_check;

ALTER TABLE event_attachments
    ADD CONSTRAINT event_attachments_mime_check CHECK (
        mime_type IN (
            'application/pdf',
            'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'image/png',
            'image/jpeg'
        )
    );
