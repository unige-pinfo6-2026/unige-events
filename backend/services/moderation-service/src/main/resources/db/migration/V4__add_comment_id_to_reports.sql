-- V4__add_comment_id_to_reports.sql — SCRUM-144.
--
-- Extends the `reports` table so a single row can target either an event OR
-- a comment (mutually exclusive, XOR enforced at the DB level).
--
-- Schema changes :
--   1) Add comment_id (nullable BIGINT).
--   2) Relax event_id NOT NULL (becomes nullable — mutex with comment_id).
--   3) Drop the old uk_report_reporter_event (event-only).
--   4) Add CHECK report_target_xor : exactly one of event_id / comment_id is non-null.
--   5) Create two partial UKs (PG 9.5+ syntax — PG16 in chart Helm) :
--        - uq_report_event_partial   (reporter_id, event_id)   WHERE event_id IS NOT NULL
--        - uq_report_comment_partial (reporter_id, comment_id) WHERE comment_id IS NOT NULL
--   6) Index idx_report_comment on (comment_id) for admin queries on a given comment.
--
-- DB-per-service strict : no FK on comment_id towards comments(id) which lives
-- in postgres-engagement. UUID/Long consistency is application-level (the
-- comment visibility is validated by EngagementServiceClient.getCommentVisibility
-- before the INSERT — cf. Décision L).
--
-- Immutable once pushed (Flyway invariant — leçon SCRUM-139 / SCRUM-99).

-- 1) Add comment_id column.
ALTER TABLE reports ADD COLUMN comment_id BIGINT NULL;

-- 2) Relax event_id NOT NULL — mutually exclusive with comment_id.
ALTER TABLE reports ALTER COLUMN event_id DROP NOT NULL;

-- 3) Drop the existing UK (event-only).
ALTER TABLE reports DROP CONSTRAINT IF EXISTS uk_report_reporter_event;

-- 4) XOR CHECK : exactly one of event_id / comment_id is non-null.
ALTER TABLE reports ADD CONSTRAINT report_target_xor
    CHECK ((event_id IS NULL) <> (comment_id IS NULL));

-- 5) Partial UKs — one per target. PostgreSQL >= 9.5 supports CREATE UNIQUE
--    INDEX with WHERE clause (chart uses PG16 — no fallback needed).
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_event_partial
    ON reports (reporter_id, event_id)
    WHERE event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_comment_partial
    ON reports (reporter_id, comment_id)
    WHERE comment_id IS NOT NULL;

-- 6) Index for admin queries filtering on comment_id.
CREATE INDEX IF NOT EXISTS idx_report_comment ON reports(comment_id);
