-- SCRUM-137 hardening — Copilot review on PR #172.
--
-- V11 added the XNOR-ish check `user_id IS NOT NULL OR session_id IS NOT NULL`
-- to forbid rows with BOTH columns null, but it accepted rows with BOTH set.
-- The service layer never produces such rows (the two dispatch branches are
-- mutually exclusive — JWT path UPSERTs by (event_id, user_id), anonymous path
-- UPSERTs by (event_id, session_id)), but the database lacked defense in
-- depth. Replace the constraint with an XOR-style check so the storage layer
-- mirrors the service contract.
--
-- Existing rows are unaffected — every row written so far carries exactly one
-- of (user_id, session_id), the new constraint is therefore satisfied for all
-- of them.

ALTER TABLE event_views
    DROP CONSTRAINT chk_event_view_user_or_session;

ALTER TABLE event_views
    ADD CONSTRAINT chk_event_view_user_xor_session
    CHECK ((user_id IS NOT NULL) <> (session_id IS NOT NULL));
