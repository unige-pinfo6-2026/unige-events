-- SCRUM-97 — Étendre la check constraint events_status_check pour autoriser
-- la valeur BANNED, ajoutée à l'enum EventStatus pour le retrait définitif d'un
-- event par modération (validation d'un signalement par un admin, ou auto-hide
-- de ModerationCleanupService au seuil de reports PENDING). État terminal côté
-- créateur : pas de republish possible (à la différence de CANCELLED).

ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check;
ALTER TABLE events ADD CONSTRAINT events_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'EXPIRED', 'BANNED'));
