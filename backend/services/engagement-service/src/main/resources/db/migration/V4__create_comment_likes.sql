-- V4__create_comment_likes.sql — SCRUM-144.
-- Backs POST/DELETE /comments/{id}/like (idempotent semantics — Décision I).
--
-- FK comment_id → comments(id) ON DELETE CASCADE : local FK (both tables
-- live in postgres-engagement). Hard-delete of a Comment row auto-purges
-- its like rows.
--
-- No FK on user_id : DB-per-service strict (piège #8). The users table
-- lives in postgres-user. UUID consistency is application-level.
--
-- UK (comment_id, user_id) enforces application-level idempotence — a
-- second POST from the same caller raises PersistenceException, caught
-- by CommentLikeService and translated to a 200 response (no double
-- increment of Comment.likeCount).
--
-- Index idx_comment_like_user on (user_id) serves the batch lookup
-- findLikedCommentIdsByUser used by CommentService.getByEvent (anti N+1,
-- Décision K).
--
-- Immutable once pushed (Flyway invariant — leçon SCRUM-139 / SCRUM-99).

CREATE SEQUENCE IF NOT EXISTS comment_likes_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS comment_likes (
    id          BIGINT      NOT NULL DEFAULT nextval('comment_likes_seq'),
    comment_id  BIGINT      NOT NULL,
    user_id     UUID        NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_comment_likes PRIMARY KEY (id),
    CONSTRAINT uq_comment_like  UNIQUE (comment_id, user_id),
    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id)
        REFERENCES comments(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comment_like_user ON comment_likes(user_id);
