-- SCRUM-139 — Création de la table comments : commentaires sur événements,
-- avec support du threading (1 niveau, parent_comment_id auto-référent nullable).
-- FK vers events(id) et users(id) sans cascade — pattern défensif assumé,
-- cohérent avec V6__create_reports.sql et V14__create_follows.sql (SCRUM-138).
--
-- Numérotation V14 : sur origin/main au moment de la rédaction, le dernier
-- migrant est V13. La PR SCRUM-138 (feature/s6-follow) tient encore son propre
-- V14__create_follows.sql ouvert ; en cas de merge avant celle-ci, il faudra
-- rebase + rename V14 -> V15 dans un commit fix(scrum-139). Cf. spec décision 2.

CREATE SEQUENCE IF NOT EXISTS comments_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS comments (
    id                BIGINT       NOT NULL DEFAULT nextval('comments_seq'),
    event_id          BIGINT       NOT NULL,
    author_id         UUID         NOT NULL,
    parent_comment_id BIGINT,
    content           TEXT         NOT NULL,
    like_count        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT pk_comments         PRIMARY KEY (id),
    CONSTRAINT fk_comments_event   FOREIGN KEY (event_id)          REFERENCES events(id),
    CONSTRAINT fk_comments_author  FOREIGN KEY (author_id)         REFERENCES users(id),
    CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_comment_id) REFERENCES comments(id)
);

-- Indexes — cf. spec SCRUM-139 décision 11.
CREATE INDEX IF NOT EXISTS idx_comment_event         ON comments(event_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent        ON comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_event_created ON comments(event_id, created_at DESC);
