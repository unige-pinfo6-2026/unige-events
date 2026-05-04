-- SCRUM-94 — Enrichissement de la table reports : ajout de l'enum ReportReason,
-- de la traçabilité de modération (reviewed_at, reviewed_by, moderation_note),
-- et renommage de l'ancienne colonne `reason` (TEXT libre) en `description`.

-- 1. Renommer l'ancienne colonne reason (TEXT libre) en description.
ALTER TABLE reports RENAME COLUMN reason TO description;

-- 2. Ajouter la nouvelle colonne reason typée enum (nullable temporairement pour le backfill).
ALTER TABLE reports ADD COLUMN reason VARCHAR(32);

-- 3. Backfill : pour les rows existantes (vides en prod, mais sécurise les envs locaux).
UPDATE reports SET reason = 'OTHER' WHERE reason IS NULL;

-- 4. Passer reason à NOT NULL après backfill.
ALTER TABLE reports ALTER COLUMN reason SET NOT NULL;

-- 5. Ajouter la CHECK constraint sur l'enum ReportReason.
ALTER TABLE reports ADD CONSTRAINT reports_reason_check
    CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER'));

-- 6. Ajouter les colonnes de traçabilité de modération.
ALTER TABLE reports ADD COLUMN moderation_note TEXT NULL;
ALTER TABLE reports ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE reports ADD COLUMN reviewed_by UUID NULL;

-- 7. FK reviewed_by → users(id).
ALTER TABLE reports ADD CONSTRAINT fk_reports_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES users(id);
