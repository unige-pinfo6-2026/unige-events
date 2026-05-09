-- SCRUM-S8 — In-app notification system.
--
-- The type column uses a VARCHAR + CHECK constraint rather than a DB-native enum
-- so that adding a new notification type does NOT require a full enum migration.
-- To add a future type, create V<N+1>__add_notification_type_<name>.sql that:
--   ALTER TABLE notifications DROP CONSTRAINT chk_notification_type;
--   ALTER TABLE notifications ADD CONSTRAINT chk_notification_type
--     CHECK (type IN ('EVENT_CANCELLED','EVENT_UPDATED','EVENT_REMINDER','NEW_ATTENDEE','<NEW_VALUE>'));
--
-- event_id is nullable: ON DELETE SET NULL preserves the notification row when
-- its parent event is hard-deleted (mirrors the parent_event_id strategy from V17).

CREATE TABLE notifications (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    UUID         NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    message    TEXT         NOT NULL,
    event_id   BIGINT,
    read       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_type
        CHECK (type IN ('EVENT_CANCELLED','EVENT_UPDATED','EVENT_REMINDER','NEW_ATTENDEE')),
    CONSTRAINT fk_notification_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE SET NULL
);

CREATE INDEX idx_notification_user  ON notifications(user_id);
CREATE INDEX idx_notification_event ON notifications(event_id);
