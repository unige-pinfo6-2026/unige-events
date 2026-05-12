CREATE SEQUENCE IF NOT EXISTS favorites_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS favorites (
    id         BIGINT    NOT NULL DEFAULT nextval('favorites_seq'),
    user_id    UUID      NOT NULL,
    event_id   BIGINT    NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT pk_favorites PRIMARY KEY (id),
    CONSTRAINT uq_favorite_user_event UNIQUE (user_id, event_id)
);
