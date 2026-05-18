-- SCRUM-99 phase 1 — Notifications in-app infrastructure.
--
-- Phase 1 type values: EVENT_UPDATED, EVENT_CANCELLED, EVENT_REMINDER, NEW_ATTENDEE.
-- Phase 2 (SCRUM-140 / SCRUM-145) will widen the CHECK constraint via
-- V2__widen_notification_type_check.sql to accept NEW_FOLLOWER, FOLLOW_REQUEST,
-- FOLLOW_ACCEPTED, COMMENT_MENTION, NEW_COMMENT — see Decision K of
-- specs_archives/specs_claude/specs_scrum-99.md.
--
-- No FK on user_id / related_user_id: notification-service owns its dedicated
-- Postgres (postgres-notification) and cannot reference the users table living
-- in postgres-user. UUID consistency across services is enforced at the
-- application layer (Decision F).
--
-- This migration is immutable once pushed (cf. specs_scrum-139 Flyway lesson).
-- Any correction goes through a new V2__... file.

CREATE SEQUENCE IF NOT EXISTS notifications_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT       NOT NULL DEFAULT nextval('notifications_seq'),
    user_id         UUID         NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    event_id        BIGINT       NULL,
    related_user_id UUID         NULL,
    message         TEXT         NOT NULL,
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    read_at         TIMESTAMP    NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT notifications_type_check CHECK (
        type IN ('EVENT_UPDATED', 'EVENT_CANCELLED', 'EVENT_REMINDER', 'NEW_ATTENDEE')
    )
);

-- Composite index serving the unread-first listing
-- (ORDER BY user_id = ?, read ASC, created_at DESC, id DESC) without sort or
-- sequential scan. read ASC pushes false before true (SQL boolean ordering).
CREATE INDEX IF NOT EXISTS idx_notification_user_read_created
    ON notifications(user_id, read, created_at DESC);

-- Secondary index for all-statuses listings or future maintenance queries
-- (cleanup of read notifications older than N days, etc.).
CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notifications(user_id, created_at DESC);
