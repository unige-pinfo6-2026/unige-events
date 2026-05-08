-- SCRUM-98 — Étendre la check constraint events_status_check pour autoriser
-- la valeur EXPIRED, ajoutée à l'enum EventStatus pour le job d'expiration
-- automatique des events publiés dont la date de fin est dépassée.

ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check;
ALTER TABLE events ADD CONSTRAINT events_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'EXPIRED'));
