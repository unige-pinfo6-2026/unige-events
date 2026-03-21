-- V2 : création de la table events
-- Note : organizer_id référence l'ID d'un utilisateur. La FK vers users(id) est omise
-- car users.id est de type UUID (incompatible avec BIGINT). À corriger lors de l'activation
-- de Flyway en production si le type de organizerId est aligné avec users.id.

CREATE TABLE events (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    location       VARCHAR(255) NOT NULL,
    start_date     TIMESTAMP    NOT NULL,
    end_date       TIMESTAMP    NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    image_url      VARCHAR(512),
    organizer_id   BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    capacity       INTEGER,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP
);

CREATE INDEX idx_event_organizer   ON events (organizer_id);
CREATE INDEX idx_event_start_date  ON events (start_date);
