-- SCRUM-138 — Création de la table follows : relation directionnelle entre deux utilisateurs.
-- Table de jointure UUID/UUID avec statut PENDING/ACCEPTED, contrainte unique sur le couple,
-- et FK vers users(id) côté DB (sans cascade — pattern défensif assumé, cf. spec décision 2).

CREATE SEQUENCE IF NOT EXISTS follows_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS follows (
    id           BIGINT       NOT NULL DEFAULT nextval('follows_seq'),
    follower_id  UUID         NOT NULL,
    followed_id  UUID         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (id),
    CONSTRAINT uq_follow_follower_followed UNIQUE (follower_id, followed_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id),
    CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users(id),
    CONSTRAINT follows_status_check CHECK (status IN ('PENDING', 'ACCEPTED'))
);

-- Index pour les requêtes inverses fréquentes (listing followers d'un user, calcul de followStatus).
CREATE INDEX IF NOT EXISTS idx_follow_followed ON follows(followed_id);
CREATE INDEX IF NOT EXISTS idx_follow_follower ON follows(follower_id);
