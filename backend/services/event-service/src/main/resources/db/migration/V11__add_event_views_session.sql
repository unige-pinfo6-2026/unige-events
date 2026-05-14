-- Allow anonymous event views with a client-generated session UUID, and
-- de-duplicate per (eventId, sessionId) for anonymous callers in addition
-- to the existing (eventId, userId) constraint for authenticated ones.
--
-- Rationale: pre-V11, recordView required an authenticated caller — the
-- counter under-reported public traffic. Post-V11, the resource accepts
-- a body { sessionId: UUID } from anonymous clients (UUID v4 persisted
-- client-side in localStorage). A row has exactly ONE of (user_id,
-- session_id) populated.

ALTER TABLE event_views
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE event_views
    ADD COLUMN session_id UUID NULL;

ALTER TABLE event_views
    DROP CONSTRAINT IF EXISTS uq_event_view_user_event;

CREATE UNIQUE INDEX uq_event_view_user_event
    ON event_views (event_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_event_view_event_session
    ON event_views (event_id, session_id)
    WHERE session_id IS NOT NULL;

ALTER TABLE event_views
    ADD CONSTRAINT chk_event_view_user_or_session
    CHECK (user_id IS NOT NULL OR session_id IS NOT NULL);
