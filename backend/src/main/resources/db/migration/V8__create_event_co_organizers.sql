CREATE SEQUENCE IF NOT EXISTS event_co_organizers_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS event_co_organizers (
    id          BIGINT       NOT NULL DEFAULT nextval('event_co_organizers_seq'),
    event_id    BIGINT       NOT NULL,
    user_id     UUID         NOT NULL,
    status      VARCHAR(255) NOT NULL,
    invited_at  TIMESTAMP,
    CONSTRAINT pk_event_co_organizers PRIMARY KEY (id),
    CONSTRAINT uq_event_co_organizers_event_user UNIQUE (event_id, user_id),
    CONSTRAINT event_co_organizers_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE INDEX IF NOT EXISTS idx_event_co_organizers_event ON event_co_organizers(event_id);
CREATE INDEX IF NOT EXISTS idx_event_co_organizers_user  ON event_co_organizers(user_id);
