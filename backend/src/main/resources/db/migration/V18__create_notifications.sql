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
--
-- notifications_seq follows the project convention (cf. V2, V3, V8 …):
-- explicit named sequence so Hibernate's validate mode finds exactly the name
-- it expects ({entity_lowercase}_seq, allocationSize = 50).

CREATE SEQUENCE IF NOT EXISTS notifications_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE notifications (
    id         BIGINT       NOT NULL DEFAULT nextval('notifications_seq'),
    user_id    UUID         NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    message    TEXT         NOT NULL,
    event_id   BIGINT,
    read       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT chk_notification_type
        CHECK (type IN ('EVENT_CANCELLED','EVENT_UPDATED','EVENT_REMINDER','NEW_ATTENDEE')),
    CONSTRAINT fk_notification_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE SET NULL
);

CREATE INDEX idx_notification_user  ON notifications(user_id);
CREATE INDEX idx_notification_event ON notifications(event_id);
