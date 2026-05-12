ALTER TABLE comments DROP CONSTRAINT IF EXISTS fk_comments_parent;
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_parent
    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE SET NULL;
