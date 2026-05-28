-- Drop the legacy `STAFF` value from `study_level`. The column is free-text
-- (no enum constraint), but the frontend `STUDY_LEVELS` const and the
-- `openapi.yaml#StudyLevel` enum no longer list it (PR #214 cleanup) — any
-- row still carrying `STAFF` would render with an empty label in the UI.
--
-- Idempotent : the predicate matches no rows once the back-fill is done.

UPDATE users SET study_level = NULL WHERE study_level = 'STAFF';
