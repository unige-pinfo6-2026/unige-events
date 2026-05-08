-- SCRUM-147 — Récurrence sur Event : ajout de 2 colonnes parent_event_id +
-- recurrence_rule à la table events. Chaque occurrence est une row events
-- standalone avec parent_event_id pointant vers le template parent.
--
-- ON DELETE SET NULL : la spec (décision 5) impose que le DELETE physique du
-- parent (après cancel) préserve les occurrences orphelines (parent_event_id
-- = NULL) — leurs inscriptions, favoris, vues et comptages sont conservés.
-- Sans cette clause, RESTRICT par défaut bloquerait le DELETE côté DB.
--
-- Numérotation V17 : sur origin/main au moment du checkout, le dernier migrant
-- est V13 ; V14 (follows, SCRUM-138 PR #154), V15 (comments, SCRUM-139 PR #156)
-- et V16 (PR concurrente connue de l'utilisateur) sont attendus mergés avant
-- cette PR. Le user a explicitement fixé la migration SCRUM-147 à V17. Si une
-- nouvelle PR concurrente prend V17 entre temps, basculer en V18 dans un commit
-- fix(scrum-147): rebase V17 -> V18. Cf. spec décision 2.

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS parent_event_id BIGINT,
    ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(500);

ALTER TABLE events
    ADD CONSTRAINT fk_events_parent
        FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_parent ON events(parent_event_id);
