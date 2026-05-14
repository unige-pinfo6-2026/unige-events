-- Allow anonymous event views with a client-generated session UUID, and
-- de-duplicate per (eventId, sessionId) for anonymous callers in addition
-- to the existing (eventId, userId) constraint for authenticated ones.
--
-- Rationale: pre-V11, recordView required an authenticated caller — the
-- counter under-reported public traffic. Post-V11, the resource accepts
-- a body { sessionId: UUID } from anonymous clients (UUID v4 persisted
-- client-side in localStorage). A row has exactly ONE of (user_id,
-- session_id) populated.
--
-- Why two FULL unique constraints (not partial unique indexes): PostgreSQL
-- requires a real CONSTRAINT (not just an index) for `ON CONFLICT ... DO
-- UPDATE`. Partial unique indexes are not recognized as conflict targets.
-- Two normal UNIQUE constraints on (event_id, user_id) and (event_id,
-- session_id) work as expected because PostgreSQL treats NULLs as
-- distinct for uniqueness — so rows with user_id NULL never collide on
-- the first constraint, and rows with session_id NULL never collide on
-- the second.

ALTER TABLE event_views
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE event_views
    ADD COLUMN session_id UUID NULL;

-- Existing constraint kept as-is (it's a normal UNIQUE — NULL user_id
-- rows never collide on it because Postgres treats NULLs as distinct).
-- We simply add a second one for the anonymous-session dedup.
ALTER TABLE event_views
    ADD CONSTRAINT uq_event_view_event_session UNIQUE (event_id, session_id);

ALTER TABLE event_views
    ADD CONSTRAINT chk_event_view_user_or_session
    CHECK (user_id IS NOT NULL OR session_id IS NOT NULL);
