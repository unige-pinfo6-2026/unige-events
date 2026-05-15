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
    CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_comment_id) REFERENCES comments(id)
);

CREATE INDEX IF NOT EXISTS idx_comment_event         ON comments(event_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent        ON comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_event_created ON comments(event_id, created_at DESC);
