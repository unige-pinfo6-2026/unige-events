CREATE SEQUENCE IF NOT EXISTS event_views_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS event_views (
    id        BIGINT    NOT NULL DEFAULT nextval('event_views_seq'),
    event_id  BIGINT    NOT NULL,
    user_id   UUID      NOT NULL,
    viewed_at TIMESTAMP,
    CONSTRAINT pk_event_views PRIMARY KEY (id),
    CONSTRAINT uq_event_view_user_event UNIQUE (event_id, user_id)
);
