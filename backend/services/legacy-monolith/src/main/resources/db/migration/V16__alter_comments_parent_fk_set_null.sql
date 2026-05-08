-- SCRUM-139 fix post-Copilot review (PR #156, comment 3207926295) — alignement
-- de la FK fk_comments_parent sur le comportement DELETE physique documenté
-- par la spec décision 17 : un parent supprimé doit faire remonter ses replies
-- en top-level (parent_comment_id = NULL) plutôt que d'être bloqué par la
-- contrainte RESTRICT par défaut.
--
-- Pourquoi un V15 séparé et pas une modification de V14 :
--   La V14__create_comments.sql avait déjà été appliquée sur l'env preview
--   (commit 5848630, premier déploiement réussi de la PR). Modifier V14 a
--   provoqué un Flyway "checksum mismatch for migration version 14" sur les
--   redéploiements suivants. La règle backend/AGENTS.md (« migration committée
--   immutable ») couvre exactement ce cas — y compris pour les PRs ouvertes
--   dont le preview deploy a une DB persistante. Le changement va donc dans
--   un V15 dédié, qui DROP la FK existante puis la recrée avec ON DELETE SET NULL.

ALTER TABLE comments DROP CONSTRAINT IF EXISTS fk_comments_parent;
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_parent
    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE SET NULL;
